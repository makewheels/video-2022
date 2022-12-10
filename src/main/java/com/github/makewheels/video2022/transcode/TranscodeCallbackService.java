package com.github.makewheels.video2022.transcode;

import cn.hutool.core.date.DateUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.models.QueryJobListResponseBody;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.transcode.aliyun.AliyunTranscodeStatus;
import com.github.makewheels.video2022.utils.DingUtil;
import com.github.makewheels.video2022.utils.M3u8Util;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 处理转码回调
 */
@Service
@Slf4j
public class TranscodeCallbackService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private TranscodeRepository transcodeRepository;

    @Resource
    private AliyunMpsService aliyunMpsService;

    @Resource
    private FileService fileService;
    @Resource
    private VideoRepository videoRepository;

    /**
     * 阿里云 云函数转码完成回调
     */
    public Result<Void> aliyunCloudFunctionTranscodeCallback(JSONObject body) {
        String jobId = body.getString("jobId");
        log.info("开始处理 阿里云 云函数转码完成回调：jobId = " + jobId);
        Transcode transcode = transcodeRepository.getByJobId(jobId);
        handleTranscodeCallback(transcode);
        return Result.ok();
    }

    /**
     * 处理阿里云视频转码回调
     */
    public void aliyunTranscodeCallback(String jobId) {
        log.info("阿里云MPS转码回调开始：jobId = " + jobId);
        Transcode transcode = transcodeRepository.getByJobId(jobId);
        handleTranscodeCallback(transcode);
    }

    /**
     * 因为阿里云的http回调收费，两块钱一个topic，那就一直不停的迭代查询job状态
     */
    public void iterateQueryAliyunTranscodeJob(Video video, Transcode transcode) {
        String jobId = transcode.getJobId();
        long duration = video.getDuration();
        long startTime = System.currentTimeMillis();
        //轮询
        for (int i = 0; i < 1000000000; i++) {
            log.info("i = " + i + " 开始睡觉");
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            //如果花了视频的15倍时长都没转完，就跳出
            if ((System.currentTimeMillis() - startTime) > 15L * duration) {
                log.error("花了视频的15倍时长都没转完，来人看看这是啥 jobId = {}, video = {}",
                        jobId, JSON.toJSONString(video));
                log.error("transcode = " + JSON.toJSONString(transcode));
                break;
            }

            //查询任务
            QueryJobListResponseBody.QueryJobListResponseBodyJobListJob job
                    = aliyunMpsService.queryTranscodeJob(jobId).getBody().getJobList().getJob().get(0);
            String jobStatus = job.getState();
            log.info("阿里云轮询查询job结果: jobStatus = {}, job = {}", jobStatus, JSON.toJSONString(job));
            //如果转码已完成，回调
            if (AliyunTranscodeStatus.isFinishedStatus(jobStatus)) {
                aliyunTranscodeCallback(jobId);
                break;
            }
        }
    }

    /**
     * 处理transcode回调，根据jobId查询百度或阿里接口获取转码情况
     * 这个处理是通用的，同时兼容百度和阿里，
     * 前面不管是那个服务商，只需根据jobId从数据库查出transcode对象传入即可
     */
    private void handleTranscodeCallback(Transcode transcode) {
        String jobId = transcode.getJobId();
        String jobStatus = null;
        String transcodeResultJson = null;

        //向对应的云服务商查询转码任务
        switch (transcode.getProvider()) {
            case TranscodeProvider.ALIYUN_MPS: {
                QueryJobListResponseBody.QueryJobListResponseBodyJobListJob job
                        = aliyunMpsService.queryTranscodeJob(jobId).getBody().getJobList().getJob().get(0);
                jobStatus = job.getState();
                transcode.setFinishTime(DateUtil.parseUTC(job.getFinishTime()));
                transcodeResultJson = JSON.toJSONString(job);
                break;
            }
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION:
                jobStatus = "FINISHED";
                transcode.setFinishTime(new Date());
                break;
        }

        //更新转码状态到数据库
        if (!StringUtils.equals(jobStatus, transcode.getStatus())) {
            transcode.setStatus(jobStatus);
            transcode.setResult(JSONObject.parseObject(transcodeResultJson));
            mongoTemplate.save(transcode);
            //通知视频转码完成
            onTranscodeFinish(transcode);
        }
    }

    /**
     * 当有一个转码job完成时回调
     */
    public void onTranscodeFinish(Transcode transcode) {
        String videoId = transcode.getVideoId();
        Video video = videoRepository.getById(videoId);
        if (video == null) return;

        //更新video状态
        updateVideoStatus(video);

        //保存m3u8文件
        saveM3u8File(video, transcode);

        //保存对象存储中的ts文件
        saveS3Files(video, transcode);

        //如果视频已就绪
        if (video.getStatus().equals(VideoStatus.READY)) {
            //通知钉钉
            DingUtil.sendMarkdown("视频就绪", video.getTitle() + "\n\n" + videoId);
        }
    }

    /**
     * 更新视频转码状态
     */
    private void updateVideoStatus(Video video) {
        //从数据库中查出，该视频对应的所有转码任务
        List<Transcode> transcodeList = transcodeRepository.getByIds(video.getTranscodeIds());
        //统计已完成数量
        long completeCount = transcodeList.stream().filter(Transcode::isFinishStatus).count();

        String videoStatus;
        //如果是部分完成
        if (completeCount > 0 && completeCount < transcodeList.size()) {
            videoStatus = VideoStatus.TRANSCODING_PARTLY_COMPLETED;
        } else if (completeCount == transcodeList.size()) {
            //如果全部完成
            videoStatus = VideoStatus.READY;
        } else {
            //如果一个都没完成
            videoStatus = VideoStatus.TRANSCODING;
        }

        //更新videoStatus到数据库
        if (!StringUtils.equals(videoStatus, video.getStatus())) {
            video.setStatus(videoStatus);
            video.setUpdateTime(new Date());
            mongoTemplate.save(video);
        }
    }

    /**
     * 保存m3u8文件
     */
    private void saveM3u8File(Video video, Transcode transcode) {
        String m3u8Key = transcode.getM3u8Key();

        File m3u8File = new File();
        m3u8File.init();

        m3u8File.setStatus(FileStatus.READY);
        m3u8File.setKey(m3u8Key);
        m3u8File.setType(FileType.TRANSCODE_M3U8);
        m3u8File.setVideoId(video.getId());
        m3u8File.setVideoType(video.getType());
        m3u8File.setUserId(video.getUserId());

        //获取m3u8文件内容
        OSSObject object = fileService.getObject(m3u8Key);
        m3u8File.setObjectInfo(object);
        log.info("保存m3u8File: {}", JSON.toJSONString(m3u8File));
        mongoTemplate.save(m3u8File);

        String m3u8FileUrl = fileService.generatePresignedUrl(m3u8Key, Duration.ofMinutes(10));
        String m3u8Content = HttpUtil.get(m3u8FileUrl);
        transcode.setM3u8Content(m3u8Content);
        mongoTemplate.save(transcode);
    }

    /**
     * 计算码率
     *
     * @param filesize   文件大小
     * @param timeLength 视频时长
     * @return
     */
    private int getBitrate(long filesize, BigDecimal timeLength) {
        BigDecimal bitrate = new BigDecimal(filesize * 8)
                .divide(timeLength, RoundingMode.HALF_UP);
        return Integer.parseInt(bitrate.toString());
    }

    /**
     * 转码完成后，更新对象存储ts碎片
     */
    private void saveS3Files(Video video, Transcode transcode) {
        String videoId = video.getId();
        String userId = video.getUserId();
        String m3u8Content = transcode.getM3u8Content();

        //获取对象存储每一个文件
        String transcodeFolder = FilenameUtils.getPath(transcode.getM3u8Key());
        List<OSSObjectSummary> objects = fileService.listAllObjects(transcodeFolder);
        Map<String, OSSObjectSummary> ossFilenameMap = objects.stream().collect(
                Collectors.toMap(e -> FilenameUtils.getName(e.getKey()), Function.identity()));

        //获取所有ts碎片文件名
        List<String> filenames = M3u8Util.getFilenames(m3u8Content);
        //获取ts时长
        Map<String, BigDecimal> tsTimeLengthMap = M3u8Util.getTsTimeLengthMap(m3u8Content);

        //遍历每一个ts文件
        List<File> tsFiles = new ArrayList<>(filenames.size());
        for (int i = 0; i < filenames.size(); i++) {
            String filename = filenames.get(i);
            File tsFile = new File();
            tsFile.init();
            tsFile.setStatus(FileStatus.READY);

            tsFile.setType(FileType.TRANSCODE_TS);
            tsFile.setUserId(userId);
            tsFile.setVideoId(videoId);
            tsFile.setVideoType(video.getType());
            tsFile.setTranscodeId(transcode.getId());
            tsFile.setResolution(transcode.getResolution());
            tsFile.setTsIndex(i);
            tsFile.setObjectInfo(ossFilenameMap.get(filename));

            //计算ts码率
            tsFile.setBitrate(getBitrate(tsFile.getSize(), tsTimeLengthMap.get(filename)));

            tsFiles.add(tsFile);
        }

        //保存所有ts文件到数据库
        log.info("保存tsFiles, 总共 {} 个", tsFiles.size());
        mongoTemplate.insertAll(tsFiles);

        //反向更新transcode的ts文件id列表
        transcode.setTsFileIds(tsFiles.stream().map(File::getId).collect(Collectors.toList()));

        //计算transcode平均码率
        long tsTotalSize = tsFiles.stream().mapToLong(File::getSize).sum();
        BigDecimal duration = new BigDecimal(video.getDuration() / 1000);
        transcode.setAverageBitrate(getBitrate(tsTotalSize, duration));

        //计算transcode最高码率
        Integer maxBitrate = tsFiles.stream().max(
                Comparator.comparing(File::getBitrate)).get().getBitrate();
        transcode.setMaxBitrate(maxBitrate);

        mongoTemplate.save(transcode);
    }

}
