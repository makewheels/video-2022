package com.github.makewheels.video2022.transcode;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.models.SubmitMediaInfoJobResponseBody;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionClient;
import com.github.makewheels.video2022.transcode.contants.Resolution;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.factory.TranscodeFactory;
import com.github.makewheels.video2022.transcode.factory.TranscodeService;
import com.github.makewheels.video2022.transcode.pipeline.ResolutionPlanner;
import com.github.makewheels.video2022.transcode.task.FcTask;
import com.github.makewheels.video2022.transcode.task.FcTaskRepository;
import com.github.makewheels.video2022.transcode.task.FcTaskStatus;
import com.github.makewheels.video2022.transcode.task.FcTaskSubmitter;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.utils.IdService;
import com.github.makewheels.video2022.utils.OssPathUtil;
import com.github.makewheels.video2022.video.bean.entity.MediaInfo;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoCodec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 发起转码。
 * <p>
 * transcode.pipeline = legacy：原有链路（MPS 探测 + 旧选档决策），行为不变。
 * transcode.pipeline = self-hosted：自建链路——CPU 函数 ffprobe 探测，探测回调后按显示短边
 * 选档，先持久化全部档位任务再逐档提交 CPU（remux）/ GPU（重编码）函数。
 */
@Service
@Slf4j
public class TranscodeLauncher {
    @Resource
    private EnvironmentService environmentService;

    @Value("${transcode.pipeline:legacy}")
    private String pipeline;

    @Value("${aliyun.oss.video.bucket}")
    private String bucket;
    @Value("${aliyun.oss.video.internal-endpoint}")
    private String internalEndpoint;

    @Value("${transcode.deadline.base-seconds:180}")
    private long deadlineBaseSeconds;
    @Value("${transcode.deadline.duration-multiplier:4}")
    private long deadlineDurationMultiplier;

    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private FileService fileService;

    @Resource
    private AliyunMpsService aliyunMpsService;
    @Resource
    private TranscodeFactory transcodeFactory;
    @Resource
    private IdService idService;

    @Resource
    private ResolutionPlanner resolutionPlanner;
    @Resource
    private FcTaskSubmitter fcTaskSubmitter;
    @Resource
    private FcTaskRepository fcTaskRepository;
    @Resource
    private CloudFunctionClient cloudFunctionClient;

    public boolean isSelfHostedEnabled() {
        return "self-hosted".equals(pipeline);
    }

    // ==================== 入口 ====================

    /**
     * 开始转码（RawFileService 上传完成入口）
     */
    public void transcodeVideo(User user, Video video) {
        if (isSelfHostedEnabled()) {
            startProbe(video);
        } else {
            legacyTranscode(user, video);
        }
    }

    // ==================== 自建链路 ====================

    /**
     * 提交 PROBE 任务到自建 CPU 函数。探测回调后由 {@link #onProbeFinished} 继续选档提交。
     */
    public void startProbe(Video video) {
        if (!cloudFunctionClient.isConfigured(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU)) {
            throw new IllegalStateException("自建链路未配置（aliyun.cf.transcode.cpu.url / invoke-secret），"
                    + "已禁止进入新链路，videoId = " + video.getId());
        }
        String videoId = video.getId();
        String sourceKey = fileService.getKeyByFileId(video.getRawFileId());

        FcTask task = fcTaskSubmitter.createTask("PROBE", videoId, null);
        task.setProvider(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU);
        task.setAttemptId(IdUtil.nanoId(16));
        task.setAttemptCount(1);
        task.setDeadline(new Date(System.currentTimeMillis() + 300_000L));

        String outputDir = OssPathUtil.getVideoPrefix(video) + "/probe/" + task.getId();
        JSONObject payload = cloudFunctionClient.buildPayload(task, null, sourceKey, outputDir,
                environmentService.getCallbackUrl("/transcode/cloudFunctionCallback"));

        fcTaskRepository.save(task);
        fcTaskSubmitter.submit(task, payload);
        log.info("已提交 PROBE 任务，videoId = {}, taskId = {}", videoId, task.getId());
    }

    /**
     * PROBE 回调处理：解析媒体信息、选档、先建全部档任务再逐档提交。
     * 由 FcTaskCallbackService 在任务身份校验通过后调用。
     */
    public void onProbeFinished(Video video, JSONObject manifest) {
        String videoId = video.getId();
        JSONObject media = manifest.getJSONObject("mediaInfo");
        Assert.notNull(media, "probe manifest 缺少 mediaInfo");

        applyProbeResult(video, media);
        mongoTemplate.save(video);

        int displayShortSide = Math.min(media.getIntValue("displayWidth"),
                media.getIntValue("displayHeight"));
        List<String> resolutions = resolutionPlanner.planResolutions(displayShortSide);
        log.info("探测完成选档：videoId = {}, displaySize = {}x{}, shortSide = {}, 档位 = {}",
                videoId, media.getIntValue("displayWidth"), media.getIntValue("displayHeight"),
                displayShortSide, resolutions);

        long durationSec = video.getMediaInfo().getDuration() == null ? 60
                : video.getMediaInfo().getDuration() / 1000;
        long deadlineMs = clampDeadline(deadlineBaseSeconds + durationSec * deadlineDurationMultiplier);
        log.info("转码提交准备：videoId = {}, deadlineSec = {}", videoId, deadlineMs / 1000);

        // 第一步：先持久化全部档位任务，避免首档快回调误判全部完成
        List<Transcode> transcodes = new ArrayList<>();
        for (String resolution : resolutions) {
            transcodes.add(createTranscodeRecord(video, resolution));
        }

        // 第二步：逐档提交（provider 决策 + 协议构建在各 TranscodeService 实现里）
        for (Transcode transcode : transcodes) {
            String provider = resolutionPlanner.decideProvider(media, transcode.getResolution());
            transcode.setProvider(provider);
            mongoTemplate.save(transcode);
            TranscodeService transcodeService = transcodeFactory.getService(provider);
            transcodeService.transcode(video, transcode);
        }
    }

    private Transcode createTranscodeRecord(Video video, String targetResolution) {
        Transcode transcode = new Transcode();
        transcode.setId(idService.getTranscodeId());
        transcode.setUserId(video.getUploaderId());
        transcode.setVideoId(video.getId());
        transcode.setResolution(targetResolution);
        transcode.setSourceKey(fileService.getKeyByFileId(video.getRawFileId()));
        transcode.setM3u8Key(OssPathUtil.getM3u8Key(video, transcode));
        mongoTemplate.save(transcode);

        //反向追加，更新video的transcodeIds
        List<String> transcodeIds = video.getTranscodeIds();
        if (transcodeIds == null) transcodeIds = new ArrayList<>();
        transcodeIds.add(transcode.getId());
        video.setTranscodeIds(transcodeIds);
        mongoTemplate.save(video);
        return transcode;
    }

    /**
     * 探测结果写入 MediaInfo：基础字段 + 扩展字段。bitrate 统一 bps。
     */
    private void applyProbeResult(Video video, JSONObject media) {
        MediaInfo mediaInfo = video.getMediaInfo();
        if (mediaInfo == null) {
            mediaInfo = new MediaInfo();
            video.setMediaInfo(mediaInfo);
        }
        mediaInfo.setWidth(media.getIntValue("width"));
        mediaInfo.setHeight(media.getIntValue("height"));
        mediaInfo.setDisplayWidth(media.getInteger("displayWidth"));
        mediaInfo.setDisplayHeight(media.getInteger("displayHeight"));
        mediaInfo.setDuration(media.getLong("durationMs"));
        mediaInfo.setVideoCodec(media.getString("videoCodec"));
        mediaInfo.setAudioCodec(media.getString("audioCodec"));
        mediaInfo.setBitrate((int) media.getLongValue("bitrateBps"));
        mediaInfo.setFrameRate(media.getString("frameRate"));
        mediaInfo.setFrameRateMode(media.getString("frameRateMode"));
        mediaInfo.setSar(media.getString("sar"));
        mediaInfo.setRotation(media.getInteger("rotation"));
        mediaInfo.setPixFmt(media.getString("pixFmt"));
        mediaInfo.setBitDepth(media.getInteger("bitDepth"));
        mediaInfo.setColorPrimaries(media.getString("colorPrimaries"));
        mediaInfo.setColorTransfer(media.getString("colorTransfer"));
        mediaInfo.setColorSpace(media.getString("colorSpace"));
        mediaInfo.setColorRange(media.getString("colorRange"));
        mediaInfo.setDynamicRange(media.getString("dynamicRange"));
        mediaInfo.setAudioTrackCount(media.getInteger("audioTrackCount"));
        mediaInfo.setAudioTracks(media.getJSONObject("audioTracks"));
        mediaInfo.setResponse(new JSONObject());
        mediaInfo.getResponse().put("probe", media);
    }

    private long clampDeadline(long seconds) {
        return Math.min(Math.max(seconds, 300), 1740) * 1000;
    }

    // ==================== legacy 链路（保持原行为） ====================

    private boolean isResolutionOverThanTarget(int width, int height, String resolution) {
        switch (resolution) {
            case Resolution.R_480P:
                return width * height > 854 * 480;
            case Resolution.R_720P:
                return width * height > 1280 * 720;
            case Resolution.R_1080P:
                return width * height > 1920 * 1080;
        }
        return false;
    }

    /**
     * 决定用谁转码
     */
    private String getTranscodeProvider(Video video, String targetResolution) {
        String videoId = video.getId();
        MediaInfo mediaInfo = video.getMediaInfo();

        //默认用阿里云MPS
        String transcodeProvider = TranscodeProvider.ALIYUN_MPS;
        if (!mediaInfo.getVideoCodec().equals(VideoCodec.H264)) {
            //如果不是h264，用阿里云
            log.info("决定用谁转码：源视频不是h264，用阿里云MPS转码, videoId = " + videoId);

        } else if (isResolutionOverThanTarget(mediaInfo.getWidth(), mediaInfo.getHeight(), targetResolution)) {
            //源视分辨率和目标分辨率不一致，用阿里云
            log.info("决定用谁转码：分辨率OverThanTarget，用阿里云MPS转码, videoId = " + videoId);

        } else if (mediaInfo.getBitrate() > 13000) {
            //如果源片码率太高，用阿里云压缩码率
            log.info("决定用谁转码：码率超标，用阿里云MPS转码, videoId = " + videoId);

        } else {
            //其它情况用阿里云 云函数
            //本地环境都用阿里云mps转码，不用回调。生产环境才用云函数
            if (environmentService.isProductionEnv()) {
                transcodeProvider = TranscodeProvider.ALIYUN_CLOUD_FUNCTION;
            }
        }
        log.info("最终决定用谁转码：transcodeProvider = {}, videoId = {}", transcodeProvider, videoId);
        return transcodeProvider;
    }

    /**
     * 创建新transcode对象
     */
    private Transcode createLegacyTranscode(User user, Video video, String targetResolution) {
        String userId = user.getId();
        String videoId = video.getId();

        //新建transcode对象，保存到数据库
        Transcode transcode = new Transcode();
        transcode.setId(idService.getTranscodeId());
        transcode.setUserId(userId);
        transcode.setVideoId(videoId);
        transcode.setResolution(targetResolution);
        String rawFileKey = fileService.getKeyByFileId(video.getRawFileId());
        transcode.setSourceKey(rawFileKey);

        //决定用谁转码
        String transcodeProvider = getTranscodeProvider(video, targetResolution);
        transcode.setProvider(transcodeProvider);

        String transcodeId = transcode.getId();
        Assert.notNull(transcodeId, "transcodeId is null");

        //设置m3u8 url
        transcode.setM3u8Key(OssPathUtil.getM3u8Key(video, transcode));
        mongoTemplate.save(transcode);
        return transcode;
    }

    /**
     * 转码单个分辨率
     */
    private void transcodeSingleResolution(User user, Video video, String targetResolution) {
        String videoId = video.getId();

        //创建新transcode对象
        Transcode transcode = createLegacyTranscode(user, video, targetResolution);
        String transcodeId = transcode.getId();

        //反向追加，更新video的transcodeIds
        List<String> transcodeIds = video.getTranscodeIds();
        if (transcodeIds == null) transcodeIds = new ArrayList<>();
        transcodeIds.add(transcodeId);
        video.setTranscodeIds(transcodeIds);

        mongoTemplate.save(video);

        //发起转码
        String transcodeProvider = transcode.getProvider();
        log.info("发起 " + targetResolution + " 转码：videoId = " + videoId + ", transcodeProvider = "
                + transcodeProvider);

        //根据供应商，发起对应转码
        TranscodeService transcodeService = transcodeFactory.getService(transcodeProvider);
        transcodeService.transcode(video, transcode);
    }

    /**
     * 加载媒体信息mediaInfo
     */
    private void loadMediaInfoIntoVideo(Video video) {
        String videoId = video.getId();
        String sourceKey = fileService.getKeyByFileId(video.getRawFileId());

        log.info("通过阿里云MPS获取视频信息，videoId = {}, title = {}", videoId, video.getTitle());
        //获取视频媒体信息，确定只用阿里云mps，不用其它供应商
        var mediaInfoResponse = aliyunMpsService.getMediaInfo(sourceKey);
        if (mediaInfoResponse == null || mediaInfoResponse.getBody() == null) {
            throw new IllegalStateException(
                    "阿里云MPS获取媒体信息失败（返回为空），videoId = " + videoId + ", sourceKey = " + sourceKey);
        }
        SubmitMediaInfoJobResponseBody body = mediaInfoResponse.getBody();
        SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJob job
                = body.getMediaInfoJob();
        log.info("阿里云MPS获取视频媒体信息，jobId ={}，接口返回：{}", job.getJobId(), JSON.toJSONString(job));

        //设置mediaInfo
        MediaInfo mediaInfo = video.getMediaInfo();
        mediaInfo.setResponse(JSONObject.parseObject(JSON.toJSONString(job)));
        SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJobProperties
                properties = job.getProperties();
        mediaInfo.setDuration((long) (Double.parseDouble(properties.getDuration()) * 1000));
        mediaInfo.setHeight(Integer.parseInt(properties.getHeight()));
        mediaInfo.setWidth(Integer.parseInt(properties.getWidth()));
        mediaInfo.setBitrate((int) Double.parseDouble(properties.getBitrate()));
        SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJobPropertiesStreams
                streams = properties.getStreams();
        mediaInfo.setVideoCodec(streams.getVideoStreamList().getVideoStream().get(0).getCodecName());

        List<SubmitMediaInfoJobResponseBody
                .SubmitMediaInfoJobResponseBodyMediaInfoJobPropertiesStreamsAudioStreamListAudioStream>
                audioStream = streams.getAudioStreamList().getAudioStream();
        // 可能没有音频流，比如无人机拍的视频
        if (CollectionUtils.isNotEmpty(audioStream)) {
            mediaInfo.setAudioCodec(audioStream.get(0).getCodecName());
        }
    }

    /**
     * 开始转码（legacy）
     */
    private void legacyTranscode(User user, Video video) {
        //加载媒体信息mediaInfo
        loadMediaInfoIntoVideo(video);
        mongoTemplate.save(video);

        //发起转码
        Integer width = video.getMediaInfo().getWidth();
        Integer height = video.getMediaInfo().getHeight();
        //720p
        if (width * height > 854 * 480) {
            transcodeSingleResolution(user, video, Resolution.R_720P);
        }
        //1080p
        if (width * height > 1280 * 720) {
            transcodeSingleResolution(user, video, Resolution.R_1080P);
        }
    }
}
