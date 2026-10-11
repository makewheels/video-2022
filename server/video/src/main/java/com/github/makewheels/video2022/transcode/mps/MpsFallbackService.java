package com.github.makewheels.video2022.transcode.mps;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.task.FcTask;
import com.github.makewheels.video2022.transcode.task.FcTaskRepository;
import com.github.makewheels.video2022.transcode.task.FcTaskStatus;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.MediaInfo;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * MPS 有限兜底：自建重试耗尽后允许一次。媒体保留要求不满足（HDR 等）不允许兜底，直接失败。
 */
@Service
@Slf4j
public class MpsFallbackService {
    @Resource
    private AliyunMpsService aliyunMpsService;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private FcTaskRepository fcTaskRepository;
    @Resource
    private FileService fileService;
    @Resource
    private MongoTemplate mongoTemplate;

    @Value("${transcode.fallback-mps.enabled:true}")
    private boolean fallbackEnabled;
    @Value("${aliyun.mps.fallback-template.720p:}")
    private String fallbackTemplate720p;
    @Value("${aliyun.mps.fallback-template.1080p:}")
    private String fallbackTemplate1080p;

    /**
     * 是否允许 MPS 兜底：开关开启、兜底模板已配置、transcode 未兜底过、
     * 源媒体为 SDR（HDR 无法由现有 H.264 模板保留，兜底会改变媒体表现 → 不允许）。
     */
    public boolean canFallback(String videoId, String transcodeId) {
        if (!fallbackEnabled) {
            log.info("MPS 兜底开关关闭 videoId = {}", videoId);
            return false;
        }
        Transcode transcode = transcodeRepository.getById(transcodeId);
        if (transcode == null) return false;
        if (transcode.getFallbackCount() != null && transcode.getFallbackCount() > 0) {
            log.info("该档已兜底过，不重复兜底 transcodeId = {}", transcodeId);
            return false;
        }
        Video video = videoRepository.getById(videoId);
        if (video == null) return false;
        MediaInfo mediaInfo = video.getMediaInfo();
        if (mediaInfo == null) return false;
        String dynamicRange = mediaInfo.getDynamicRange();
        if (dynamicRange != null && !"SDR".equals(dynamicRange)) {
            log.info("源为 {}，MPS 模板无法保留，不允许兜底 transcodeId = {}", dynamicRange, transcodeId);
            return false;
        }
        String templateId = templateFor(transcode.getResolution());
        if (templateId == null || templateId.isBlank()) {
            log.warn("兜底模板未配置（aliyun.mps.fallback-template.*），不允许兜底 transcodeId = {}",
                    transcodeId);
            return false;
        }
        return true;
    }

    /**
     * 提交 MPS 兜底任务。原子抢占 fallbackCount（0 → 1）防止重复提交。
     */
    public boolean fallback(FcTask task) {
        String transcodeId = task.getRefId();
        Transcode transcode = transcodeRepository.getById(transcodeId);
        if (transcode == null) return false;

        // 原子抢占兜底名额：只有第一个 fallbackCount 0→1 的调用者真正提交
        Query query = Query.query(Criteria.where("id").is(transcodeId)
                .orOperator(Criteria.where("fallbackCount").isNull(),
                        Criteria.where("fallbackCount").is(0)));
        boolean grabbed = mongoTemplate.updateFirst(query,
                new Update().set("fallbackCount", 1), Transcode.class).getModifiedCount() == 1;
        if (!grabbed) {
            log.info("兜底名额已被占用，跳过 transcodeId = {}", transcodeId);
            return false;
        }

        String jobId = aliyunMpsService.submitFallbackTranscodeJob(
                transcode.getSourceKey(), transcode.getM3u8Key(), transcode.getResolution());
        if (jobId == null) {
            log.error("MPS 兜底提交失败 transcodeId = {}", transcodeId);
            return false;
        }

        transcode.setJobId(jobId);
        transcode.setCurrentProvider(TranscodeProvider.ALIYUN_MPS);
        transcode.setM3u8Key(transcode.getM3u8Key());
        mongoTemplate.save(transcode);

        // FcTask 转 FALLBACK_PENDING 终态记录（兜底结果走 MPS 轮询/回调链路）
        fcTaskRepository.finish(task.getId(), task.getAttemptId(),
                FcTaskStatus.FAILED, new java.util.Date(),
                "自建重试耗尽，已转 MPS 兜底（jobId = " + jobId + "）");
        log.info("MPS 兜底已提交 transcodeId = {}, mpsJobId = {}", transcodeId, jobId);
        return true;
    }

    private String templateFor(String resolution) {
        return switch (resolution) {
            case "720p" -> fallbackTemplate720p;
            case "1080p" -> fallbackTemplate1080p;
            default -> null;
        };
    }
}
