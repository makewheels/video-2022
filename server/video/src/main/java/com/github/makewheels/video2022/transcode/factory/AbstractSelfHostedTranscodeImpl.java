package com.github.makewheels.video2022.transcode.factory;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionClient;
import com.github.makewheels.video2022.transcode.pipeline.ResolutionPlanner;
import com.github.makewheels.video2022.transcode.task.FcTask;
import com.github.makewheels.video2022.transcode.task.FcTaskRepository;
import com.github.makewheels.video2022.transcode.task.FcTaskSubmitter;
import com.github.makewheels.video2022.utils.OssPathUtil;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;

import jakarta.annotation.Resource;
import java.util.Date;

/**
 * 自建云函数转码公共实现：构建协议 v1 payload 并提交，CPU（remux）/ GPU（重编码）共用。
 * probe 结果从 video.mediaInfo.response.probe 读取（PROBE 回调时写入）。
 */
@Slf4j
public abstract class AbstractSelfHostedTranscodeImpl implements TranscodeService {
    @Resource
    protected EnvironmentService environmentService;
    @Resource
    protected FileService fileService;
    @Resource
    protected CloudFunctionClient cloudFunctionClient;
    @Resource
    protected FcTaskSubmitter fcTaskSubmitter;
    @Resource
    protected FcTaskRepository fcTaskRepository;
    @Resource
    protected ResolutionPlanner resolutionPlanner;
    @Resource
    protected TranscodeRepository transcodeRepository;
    @Resource
    protected MongoTemplate mongoTemplate;

    @Value("${transcode.deadline.base-seconds:180}")
    private long deadlineBaseSeconds;
    @Value("${transcode.deadline.duration-multiplier:4}")
    private long deadlineDurationMultiplier;

    /** 子类声明自己服务的 provider */
    protected abstract String provider();

    @Override
    public void transcode(Video video, Transcode transcode) {
        String videoId = video.getId();
        JSONObject media = video.getMediaInfo() == null
                || video.getMediaInfo().getResponse() == null
                || video.getMediaInfo().getResponse().getJSONObject("probe") == null
                ? null : video.getMediaInfo().getResponse().getJSONObject("probe");
        if (media == null) {
            throw new IllegalStateException("缺少 probe 结果，无法提交自建转码任务, videoId = " + videoId);
        }

        FcTask task = fcTaskSubmitter.createTask("TRANSCODE", videoId, transcode.getId());
        task.setProvider(provider());
        task.setAttemptId(IdUtil.nanoId(16));
        task.setAttemptCount(1);
        long durationSec = video.getMediaInfo().getDuration() == null ? 60
                : video.getMediaInfo().getDuration() / 1000;
        long deadlineSec = deadlineBaseSeconds + durationSec * deadlineDurationMultiplier;
        deadlineSec = Math.min(Math.max(deadlineSec, 300), 1740);
        task.setDeadline(new Date(System.currentTimeMillis() + deadlineSec * 1000));
        fcTaskRepository.save(task);

        transcode.setTaskId(task.getId());
        transcode.setCurrentProvider(provider());
        mongoTemplate.save(transcode);

        String outputDir = OssPathUtil.getTranscodePrefix(video) + "/" + transcode.getId();
        JSONObject payload = cloudFunctionClient.buildPayload(task, transcode.getId(),
                transcode.getSourceKey(), outputDir,
                environmentService.getCallbackUrl("/transcode/cloudFunctionCallback"));
        payload.put("profile", transcode.getResolution());
        payload.put("playlistKey", transcode.getM3u8Key());
        payload.put("mediaPolicy",
                resolutionPlanner.buildMediaPolicy(media, transcode.getResolution()));

        log.info("提交自建转码任务：videoId = {}, transcodeId = {}, provider = {}, taskId = {}",
                videoId, transcode.getId(), provider(), task.getId());
        fcTaskSubmitter.submit(task, payload);
    }

    /**
     * 自建链路回调统一走 /transcode/cloudFunctionCallback → FcTaskCallbackService，
     * 不经过本接口；误调用说明链路配置错误，明确失败。
     */
    @Override
    public void callback(String jobId) {
        throw new UnsupportedOperationException(
                "自建云函数回调不支持 callback(jobId) 入口, provider = " + provider());
    }
}
