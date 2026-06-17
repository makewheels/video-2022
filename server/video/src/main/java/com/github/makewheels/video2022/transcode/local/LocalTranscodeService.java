package com.github.makewheels.video2022.transcode.local;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.check.CheckService;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.transcode.TranscodeCallbackService;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.contants.TranscodeStatus;
import com.github.makewheels.video2022.transcode.local.dto.CreateLocalTranscodeRequest;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.utils.IdService;
import com.github.makewheels.video2022.utils.OssPathUtil;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.MediaInfo;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.TranscodeMode;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 本地转码服务：客户端用本机 FFmpeg 转好 HLS 后回传，服务端只做登记，全程不调用任何云转码（MPS / 云函数）。
 *
 * <pre>
 * 流程：
 *   1. createTranscode：登记一档分辨率的 Transcode，写入源片 mediaInfo，返回 m3u8Key / outputDir / OSS 上传凭证
 *   2. 客户端把本机转好的 m3u8 + ts 直传到 outputDir
 *   3. finishTranscode：复用 {@link TranscodeCallbackService#onTranscodeFinish} 扫描 OSS 登记 ts、置视频就绪
 * </pre>
 */
@Service
@Slf4j
public class LocalTranscodeService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private FileService fileService;
    @Resource
    private IdService idService;
    @Resource
    private CheckService checkService;
    @Resource
    private TranscodeCallbackService transcodeCallbackService;

    /**
     * 登记一档分辨率的本地转码任务，返回上传目标与凭证
     */
    public JSONObject createTranscode(CreateLocalTranscodeRequest request) {
        String videoId = request.getVideoId();
        checkService.checkVideoExist(videoId);
        checkService.checkVideoBelongsToUser(videoId, UserHolder.getUserId());

        Video video = videoRepository.getById(videoId);
        if (!TranscodeMode.isLocal(video.getTranscodeMode())) {
            throw new VideoException(ErrorCode.VIDEO_CREATE_ARG_ILLEGAL, "该视频不是本地转码模式");
        }
        Assert.notNull(request.getDurationMs(), "durationMs 必填，用于计算码率");

        // 首次调用写入源片媒体信息（多档调用幂等）
        loadMediaInfoIfAbsent(video, request);

        // 视频进入转码中
        if (!VideoStatus.READY.equals(video.getStatus())) {
            video.setStatus(VideoStatus.TRANSCODING);
        }

        // 新建 Transcode 对象
        Transcode transcode = new Transcode();
        transcode.setId(idService.getTranscodeId());
        transcode.setUserId(video.getUploaderId());
        transcode.setVideoId(videoId);
        transcode.setProvider(TranscodeProvider.LOCAL);
        transcode.setStatus(VideoStatus.TRANSCODING);
        transcode.setResolution(request.getResolution());
        transcode.setSourceKey(fileService.getKeyByFileId(video.getRawFileId()));
        transcode.setM3u8Key(OssPathUtil.getM3u8Key(video, transcode));
        mongoTemplate.save(transcode);

        // 追加到 video.transcodeIds
        List<String> transcodeIds = video.getTranscodeIds();
        if (transcodeIds == null) transcodeIds = new ArrayList<>();
        transcodeIds.add(transcode.getId());
        video.setTranscodeIds(transcodeIds);
        mongoTemplate.save(video);

        String m3u8Key = transcode.getM3u8Key();
        String outputDir = m3u8Key.substring(0, m3u8Key.lastIndexOf("/"));

        // 上传凭证：客户端凭此把 m3u8 + ts 直传到 outputDir
        JSONObject credentials = fileService.getUploadCredentialsByKey(m3u8Key);

        JSONObject response = new JSONObject();
        response.put("transcodeId", transcode.getId());
        response.put("resolution", transcode.getResolution());
        response.put("m3u8Key", m3u8Key);
        response.put("outputDir", outputDir);
        // ts 命名约定，与 MPS 输出保持一致：{transcodeId}-00000.ts
        response.put("tsFilenameTemplate", transcode.getId() + "-%05d.ts");
        response.put("uploadCredentials", credentials);
        log.info("登记本地转码任务，videoId = {}, transcodeId = {}, resolution = {}, m3u8Key = {}",
                videoId, transcode.getId(), transcode.getResolution(), m3u8Key);
        return response;
    }

    /**
     * 客户端转码产物上传完成后回调：复用现有登记逻辑
     */
    public void finishTranscode(String transcodeId) {
        Transcode transcode = transcodeRepository.getById(transcodeId);
        if (transcode == null) {
            throw new VideoException(ErrorCode.VIDEO_NOT_EXIST, "转码任务不存在: " + transcodeId);
        }
        if (!TranscodeProvider.LOCAL.equals(transcode.getProvider())) {
            throw new VideoException(ErrorCode.VIDEO_CREATE_ARG_ILLEGAL, "该转码任务不是本地转码");
        }
        checkService.checkVideoBelongsToUser(transcode.getVideoId(), UserHolder.getUserId());

        transcode.setStatus(TranscodeStatus.FINISHED);
        transcode.setFinishTime(new Date());
        mongoTemplate.save(transcode);

        log.info("本地转码产物回传完成，开始登记，transcodeId = {}, videoId = {}",
                transcodeId, transcode.getVideoId());
        transcodeCallbackService.onTranscodeFinish(transcode);
    }

    private void loadMediaInfoIfAbsent(Video video, CreateLocalTranscodeRequest request) {
        MediaInfo mediaInfo = video.getMediaInfo();
        if (mediaInfo == null) {
            mediaInfo = new MediaInfo();
            video.setMediaInfo(mediaInfo);
        }
        if (mediaInfo.getDuration() != null) {
            return;
        }
        mediaInfo.setWidth(request.getWidth());
        mediaInfo.setHeight(request.getHeight());
        mediaInfo.setDuration(request.getDurationMs());
        mediaInfo.setVideoCodec(request.getVideoCodec());
        mediaInfo.setAudioCodec(request.getAudioCodec());
        mediaInfo.setBitrate(request.getBitrate());
    }
}
