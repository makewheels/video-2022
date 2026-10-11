package com.github.makewheels.video2022.transcode;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.github.makewheels.video2022.cover.CoverRepository;
import com.github.makewheels.video2022.cover.CoverService;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.openapi.webhook.WebhookEventPublisher;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.contants.TranscodeStatus;
import com.github.makewheels.video2022.transcode.task.FcTask;
import com.github.makewheels.video2022.transcode.task.FcTaskStatus;
import com.github.makewheels.video2022.utils.IdService;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.service.VideoReadyService;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.file.TsFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 处理转码完成登记：产物校验 → 幂等登记（File/TsFile）→ 更新视频状态。
 * 登记通过数据库条件更新认领，重复与并发回调只登记一次。
 */
@Service
@Slf4j
public class TranscodeCallbackService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private FileService fileService;
    @Resource
    private VideoReadyService videoReadyService;
    @Resource
    private WebhookEventPublisher webhookEventPublisher;
    @Resource
    private IdService idService;
    @Resource
    private CoverRepository coverRepository;
    @Resource
    private CoverService coverService;
    @Resource
    private FileRepository fileRepository;
    @Resource
    private TsFileRepository tsFileRepository;

    /**
     * 当有一个转码job完成时回调（MPS / 旧云函数 / LOCAL 通道共用）
     */
    public void onTranscodeFinish(Transcode transcode) {
        String videoId = transcode.getVideoId();

        Video video = videoRepository.getById(videoId);
        if (video == null) return;

        // 先登记产物，再统计状态：登记失败不能让视频进入就绪
        saveM3u8File(video, transcode);
        saveS3Files(video, transcode);

        //更新video状态
        updateVideoStatus(video);

        // 触发 webhook: 转码完成
        webhookEventPublisher.publishTranscodeCompleted(videoId, transcode.getId(), "");

        // 回调视频就绪
        if (VideoStatus.READY.equals(video.getStatus())) {
            videoReadyService.onVideoReady(video.getId());
        }
    }

    /**
     * 自建链路转码成功回调：先校验产物（OSS 实物与 manifest 交叉验证），再幂等登记。
     */
    public void onSelfHostedTranscodeFinish(FcTask task, JSONObject manifest) {
        String transcodeId = task.getRefId();
        Transcode transcode = transcodeRepository.getById(transcodeId);
        if (transcode == null) {
            log.error("TRANSCODE 回调找不到 transcodeId = {}", transcodeId);
            return;
        }
        Video video = videoRepository.getById(transcode.getVideoId());
        if (video == null) {
            log.error("TRANSCODE 回调找不到视频 videoId = {}", transcode.getVideoId());
            return;
        }

        // 1. 产物校验：不通过则按失败处理（转 RETRY_WAIT，由恢复流程重试/兜底）
        String validationError = validateOutput(transcode, manifest);
        if (validationError != null) {
            log.warn("产物校验失败 transcodeId = {}: {}", transcodeId, validationError);
            markTranscodeFailed(transcode, validationError);
            updateVideoStatus(video);
            return;
        }

        // 2. 原子认领登记权：重复/并发回调只有一次真正登记
        boolean claimed = claimRegistering(transcodeId);
        if (!claimed) {
            log.info("登记权已被认领或已终态，忽略重复回调 transcodeId = {}", transcodeId);
            return;
        }

        // 3. 写入真实输出信息 + 幂等登记
        applyActualOutput(transcode, manifest);
        saveM3u8File(video, transcode);
        saveS3Files(video, transcode);
        finishTranscode(transcode, video);
    }

    /**
     * 封面任务成功回调：下载图片信息，幂等更新 Cover/File，不改视频转码状态。
     */
    public void onFcCoverFinished(FcTask task, JSONObject manifest) {
        String coverId = task.getRefId();
        var cover = coverRepository.getById(coverId);
        if (cover == null) {
            log.warn("COVER 回调找不到 coverId = {}", coverId);
            return;
        }
        // 重复回调幂等：已 READY 直接忽略
        if ("Success".equals(cover.getStatus())) return;
        String imageKey = manifest.getString("imageKey");
        if (imageKey == null || !ossObjectExists(imageKey)) {
            log.warn("COVER 产物缺失 coverId = {}, imageKey = {}", coverId, imageKey);
            return;
        }
        coverService.applyFcCoverResult(cover, manifest);
    }

    /**
     * 任务最终失败：transcode 置 FAILED，不参与成功统计
     */
    public void onFcTaskFailed(FcTask task, String errorMessage) {
        if (!"TRANSCODE".equals(task.getOperation())) return;
        Transcode transcode = transcodeRepository.getById(task.getRefId());
        if (transcode == null) return;
        markTranscodeFailed(transcode, errorMessage);
        Video video = videoRepository.getById(transcode.getVideoId());
        if (video != null) updateVideoStatus(video);
    }

    /**
     * 产物校验：playlist 存在、含 ENDLIST、引用分片在 OSS 全部存在且非空。
     * 返回 null 表示通过，否则返回错误摘要。
     */
    private String validateOutput(Transcode transcode, JSONObject manifest) {
        String playlistKey = manifest.getString("playlistKey");
        if (playlistKey == null || !playlistKey.equals(transcode.getM3u8Key())) {
            return "manifest playlistKey 与任务不符: " + playlistKey;
        }
        String m3u8Content = readObjectString(playlistKey);
        if (m3u8Content == null || m3u8Content.isBlank()) {
            return "playlist 不存在或为空: " + playlistKey;
        }
        if (!m3u8Content.contains("#EXT-X-ENDLIST")) {
            return "playlist 缺少 ENDLIST";
        }
        // 分片实物校验
        List<String> segmentNames = M3u8Util.getFilenames(m3u8Content);
        if (segmentNames.isEmpty()) {
            return "playlist 无分片";
        }
        String folder = FilenameUtils.getPath(playlistKey);
        for (String name : segmentNames) {
            String key = folder + name;
            if (!ossObjectExists(key)) {
                return "分片缺失: " + name;
            }
        }
        // init segment（fMP4）
        String initUri = M3u8Util.getInitSegmentUri(m3u8Content);
        if (initUri != null && !ossObjectExists(folder + initUri)) {
            return "init segment 缺失: " + initUri;
        }
        return null;
    }

    /**
     * 原子认领登记权：status 从非终态（CREATED/TRANSCODING）置 REGISTERING
     */
    private boolean claimRegistering(String transcodeId) {
        Query query = Query.query(Criteria.where("id").is(transcodeId)
                .and("status").nin(TranscodeStatus.REGISTERING, TranscodeStatus.FINISHED,
                        TranscodeStatus.FAILED));
        return mongoTemplate.updateFirst(query,
                new Update().set("status", TranscodeStatus.REGISTERING), Transcode.class)
                .getModifiedCount() == 1;
    }

    private void applyActualOutput(Transcode transcode, JSONObject manifest) {
        transcode.setActualWidth(manifest.getInteger("width"));
        transcode.setActualHeight(manifest.getInteger("height"));
        transcode.setActualFrameRate(manifest.getString("frameRate"));
        transcode.setActualDynamicRange(manifest.getString("dynamicRange"));
        transcode.setActualCodecs(manifest.getString("hlsCodecs"));
    }

    private void finishTranscode(Transcode transcode, Video video) {
        transcode.setStatus(TranscodeStatus.FINISHED);
        transcode.setFinishTime(new Date());
        mongoTemplate.save(transcode);

        updateVideoStatus(video);
        webhookEventPublisher.publishTranscodeCompleted(video.getId(), transcode.getId(), "");
        if (VideoStatus.READY.equals(video.getStatus())) {
            videoReadyService.onVideoReady(video.getId());
        }
    }

    /**
     * transcode 置失败终态（条件更新，不覆盖已成功）
     */
    private void markTranscodeFailed(Transcode transcode, String errorMessage) {
        Query query = Query.query(Criteria.where("id").is(transcode.getId())
                .and("status").nin(TranscodeStatus.FINISHED));
        mongoTemplate.updateFirst(query,
                new Update().set("status", TranscodeStatus.FAILED)
                        .set("errorMessage", errorMessage)
                        .set("finishTime", new Date()),
                Transcode.class);
    }

    /**
     * 更新视频转码状态：按成功（已登记）/失败统计。
     * 全部成功 → READY；全部失败 → TRANSCODE_FAILED；混合终态或进行中 → 部分完成/转码中。
     */
    private void updateVideoStatus(Video video) {
        List<Transcode> transcodeList = transcodeRepository.getByIds(video.getTranscodeIds());
        long successCount = transcodeList.stream().filter(Transcode::isSuccessStatus).count();
        long failedCount = transcodeList.stream()
                .filter(t -> TranscodeStatus.FAILED.equals(t.getStatus())).count();

        String videoStatus;
        if (successCount == transcodeList.size()) {
            videoStatus = VideoStatus.READY;
        } else if (failedCount == transcodeList.size()) {
            videoStatus = VideoStatus.TRANSCODE_FAILED;
        } else if (successCount > 0) {
            videoStatus = VideoStatus.TRANSCODING_PARTLY_COMPLETE;
        } else {
            videoStatus = VideoStatus.TRANSCODING;
        }

        //只允许向前推进，不允许把 READY 改回去（旧状态快照/晚到回调防护）
        if (!StringUtils.equals(videoStatus, video.getStatus())
                && !VideoStatus.READY.equals(video.getStatus())) {
            video.setStatus(videoStatus);
            videoRepository.updateStatus(video.getId(), videoStatus);
        }
    }

    /**
     * 保存m3u8文件（幂等：按 key 查重）
     */
    private File saveM3u8File(Video video, Transcode transcode) {
        String m3u8Key = transcode.getM3u8Key();

        // 获取m3u8文件内容
        String m3u8FileUrl = fileService.generatePresignedUrl(m3u8Key, Duration.ofMinutes(10));
        String m3u8Content = HttpUtil.get(m3u8FileUrl);
        transcode.setM3u8Content(m3u8Content);

        File existed = fileRepository.getByKey(m3u8Key);
        if (existed != null) {
            log.info("m3u8File 已存在，跳过重复登记 key = {}", m3u8Key);
            return existed;
        }

        File m3u8File = new File();
        m3u8File.setId(idService.getFileId());
        m3u8File.setFileStatus(FileStatus.READY);
        m3u8File.setKey(m3u8Key);
        m3u8File.setFileType(FileType.TRANSCODE_M3U8);
        m3u8File.setVideoId(video.getId());
        m3u8File.setVideoType(video.getVideoType());
        m3u8File.setUploaderId(video.getUploaderId());

        OSSObject object = fileService.getObject(m3u8Key);
        m3u8File.setObjectInfo(object);

        Assert.notNull(m3u8File.getId(), "m3u8File id is null");
        log.info("保存m3u8File: " + JSON.toJSONString(m3u8File));
        mongoTemplate.save(m3u8File);
        return m3u8File;
    }

    /**
     * 计算码率
     */
    private int calculateBitrate(long filesize, BigDecimal timeLength) {
        BigDecimal bitrate = new BigDecimal(filesize * 8)
                .divide(timeLength, RoundingMode.HALF_UP);
        return Integer.parseInt(bitrate.toString());
    }

    /**
     * 生成对象存储中的分片记录（幂等：按 transcodeId+tsIndex 查重；fMP4 init segment 以 tsIndex=-1 登记）
     */
    private List<TsFile> createTsFiles(Video video, Transcode transcode) {
        String m3u8Content = transcode.getM3u8Content();

        //获取对象存储每一个文件
        String transcodeFolder = FilenameUtils.getPath(transcode.getM3u8Key());
        List<OSSObjectSummary> objects = fileService.findObjects(transcodeFolder);
        Map<String, OSSObjectSummary> ossFilenameMap = objects.stream().collect(
                Collectors.toMap(e -> FilenameUtils.getName(e.getKey()), Function.identity()));

        //获取所有分片文件名
        List<String> filenames = M3u8Util.getFilenames(m3u8Content);
        //获取分片时长
        Map<String, BigDecimal> tsTimeLengthMap = M3u8Util.getTsTimeLengthMap(m3u8Content);

        List<TsFile> tsFiles = new ArrayList<>(filenames.size() + 1);
        String initUri = M3u8Util.getInitSegmentUri(m3u8Content);
        if (initUri != null) {
            TsFile initFile = buildTsFile(video, transcode, -1, null,
                    ossFilenameMap.get(initUri));
            if (initFile != null) tsFiles.add(initFile);
        }

        //遍历每一个分片文件
        for (int i = 0; i < filenames.size(); i++) {
            String filename = filenames.get(i);
            TsFile tsFile = buildTsFile(video, transcode, i,
                    tsTimeLengthMap.get(filename), ossFilenameMap.get(filename));
            if (tsFile != null) tsFiles.add(tsFile);
        }
        return tsFiles;
    }

    private TsFile buildTsFile(Video video, Transcode transcode, int index,
                               BigDecimal timeLength, OSSObjectSummary summary) {
        // 幂等：同 transcode + index 已登记则跳过
        TsFile existed = tsFileRepository.getByTranscodeIdAndIndex(transcode.getId(), index);
        if (existed != null) return null;

        if (summary == null) {
            log.warn("分片在 OSS 列举中缺失，跳过登记 transcodeId = {}, tsIndex = {}",
                    transcode.getId(), index);
            return null;
        }

        TsFile tsFile = new TsFile();
        tsFile.setId(idService.getTsFileId());
        tsFile.setFileStatus(FileStatus.READY);
        tsFile.setFileType(FileType.TRANSCODE_TS);
        tsFile.setUploaderId(video.getUploaderId());
        tsFile.setVideoId(video.getId());
        tsFile.setVideoType(video.getVideoType());
        tsFile.setTranscodeId(transcode.getId());
        tsFile.setResolution(transcode.getResolution());
        tsFile.setTsIndex(index);
        tsFile.setObjectInfo(summary);

        if (timeLength != null && tsFile.getSize() != null) {
            //计算分片码率
            Long size = tsFile.getSize();
            tsFile.setBitrate(calculateBitrate(size, timeLength));
        }
        return tsFile;
    }

    /**
     * 转码完成后，更新对象存储分片记录
     */
    private void saveS3Files(Video video, Transcode transcode) {
        List<TsFile> tsFiles = createTsFiles(video, transcode);

        List<TsFile> newFiles = tsFiles.stream().filter(f -> f.getId() != null).collect(Collectors.toList());
        if (!newFiles.isEmpty()) {
            log.info("保存tsFiles, 总共 {} 个", newFiles.size());
            mongoTemplate.insertAll(newFiles);
        }

        // 已登记的全量分片（含历史），保证重复回调时统计完整
        List<TsFile> allFiles = tsFileRepository.getByTranscodeId(transcode.getId());
        //反向更新transcode的ts文件id列表
        transcode.setTsFileIds(allFiles.stream().map(TsFile::getId).collect(Collectors.toList()));

        //计算平均码率（无音轨或init-only时容错）
        List<TsFile> segments = allFiles.stream()
                .filter(f -> f.getTsIndex() != null && f.getTsIndex() >= 0 && f.getSize() != null)
                .collect(Collectors.toList());
        if (!segments.isEmpty() && video.getMediaInfo() != null
                && video.getMediaInfo().getDuration() != null) {
            long totalSize = segments.stream().mapToLong(TsFile::getSize).sum();
            BigDecimal duration = new BigDecimal(video.getMediaInfo().getDuration() / 1000);
            transcode.setAverageBitrate(calculateBitrate(totalSize, duration));
            //计算最高码率
            Integer maxBitrate = segments.stream()
                    .filter(f -> f.getBitrate() != null)
                    .max(Comparator.comparing(TsFile::getBitrate))
                    .map(TsFile::getBitrate)
                    .orElse(null);
            transcode.setMaxBitrate(maxBitrate);
        } else {
            // 缺少时长信息时不估算平均码率，留空待有数据后补算
            log.warn("缺少时长或分片数据，跳过码率计算 transcodeId = {}", transcode.getId());
        }

        mongoTemplate.save(transcode);
    }

    private boolean ossObjectExists(String key) {
        try {
            OSSObject object = fileService.getObject(key);
            if (object == null) return false;
            long size = object.getObjectMetadata() == null ? 0 : object.getObjectMetadata().getContentLength();
            object.getObjectContent().close();
            return size > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String readObjectString(String key) {
        try (OSSObject object = fileService.getObject(key)) {
            if (object == null || object.getObjectContent() == null) return null;
            return new String(object.getObjectContent().readAllBytes(), "UTF-8");
        } catch (Exception e) {
            log.warn("读取对象失败 key = {}: {}", key, e.getMessage());
            return null;
        }
    }
}
