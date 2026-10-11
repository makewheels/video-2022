package com.github.makewheels.video2022.transcode.task;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.transcode.TranscodeCallbackService;
import com.github.makewheels.video2022.transcode.TranscodeLauncher;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.mps.MpsFallbackService;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 自建云函数回调统一入口（POST /transcode/cloudFunctionCallback 的处理逻辑）。
 * <p>
 * 职责：校验任务身份与 attempt、下载产物 manifest、按 operation 分发；
 * 失败时按 attempt 轮次决定重试或 MPS 兜底。重复与晚到回调记录 ignored，不重复登记。
 */
@Service
@Slf4j
public class FcTaskCallbackService {
    @Resource
    private FcTaskRepository fcTaskRepository;
    @Resource
    private FileService fileService;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private TranscodeLauncher transcodeLauncher;
    @Resource
    private TranscodeCallbackService transcodeCallbackService;
    @Resource
    private MpsFallbackService mpsFallbackService;

    @Value("${transcode.retry.backoff-ms:10000}")
    private long retryBackoffMs;
    @Value("${transcode.retry.jitter-ms:3000}")
    private long retryJitterMs;

    /**
     * 处理回调 body。返回值用于 HTTP 响应：404 未知任务、403 非法字段、200 正常（含 ignored）。
     */
    public int handleCallback(JSONObject body) {
        String taskId = body.getString("taskId");
        String attemptId = body.getString("attemptId");
        String status = body.getString("status");
        if (taskId == null || attemptId == null || status == null) {
            log.warn("回调缺少必要字段 taskId/attemptId/status");
            return 400;
        }

        FcTask task = fcTaskRepository.getById(taskId);
        if (task == null) {
            log.warn("回调任务不存在 taskId = {}", taskId);
            return 404;
        }
        if (!attemptId.equals(task.getAttemptId())) {
            // 晚到的旧 attempt 回调：稳定 2xx，记录 ignored，不触发重试风暴
            log.info("晚到回调忽略 taskId = {}, 回调 attempt = {}, 当前 attempt = {}",
                    taskId, attemptId, task.getAttemptId());
            return 200;
        }
        if (FcTaskStatus.isFinal(task.getStatus()) && "SUCCEEDED".equals(status)) {
            log.info("重复成功回调忽略 taskId = {}, status = {}", taskId, task.getStatus());
            return 200;
        }

        if ("SUCCEEDED".equals(status)) {
            return handleSucceeded(task, body);
        }
        if ("FAILED".equals(status)) {
            return handleFailed(task, body.getString("errorMessage"));
        }
        log.warn("未知回调 status = {} taskId = {}", status, taskId);
        return 400;
    }

    private int handleSucceeded(FcTask task, JSONObject body) {
        String manifestKey = body.getString("outputManifestKey");
        if (manifestKey == null) {
            log.warn("成功回调缺少 outputManifestKey taskId = {}", task.getId());
            return handleFailed(task, "成功回调缺少 outputManifestKey");
        }
        JSONObject manifest = downloadManifest(manifestKey);
        if (manifest == null) {
            return handleFailed(task, "产物 manifest 读取失败: " + manifestKey);
        }

        boolean finished = fcTaskRepository.finish(task.getId(), task.getAttemptId(),
                FcTaskStatus.SUCCEEDED, new Date(), null);
        if (!finished) {
            log.info("成功回调认领失败（并发或已终态），忽略 taskId = {}", task.getId());
            return 200;
        }

        switch (task.getOperation()) {
            case "PROBE" -> {
                Video video = videoRepository.getById(task.getVideoId());
                if (video == null) {
                    log.error("PROBE 回调找不到视频 taskId = {}, videoId = {}", task.getId(), task.getVideoId());
                    return 200;
                }
                transcodeLauncher.onProbeFinished(video, manifest);
            }
            case "TRANSCODE" -> transcodeCallbackService.onSelfHostedTranscodeFinish(task, manifest);
            case "COVER" -> transcodeCallbackService.onFcCoverFinished(task, manifest);
            default -> log.error("未知 operation = {} taskId = {}", task.getOperation(), task.getId());
        }
        return 200;
    }

    /**
     * 失败回调：还有重试额度 → 退避后重提交；耗尽 → TRANSCODE 尝试 MPS 兜底，否则终态失败。
     */
    private int handleFailed(FcTask task, String errorMessage) {
        log.warn("云函数任务失败 taskId = {}, attemptCount = {}/{}, error = {}",
                task.getId(), task.getAttemptCount(), task.getMaxAttempts(), errorMessage);
        int nextAttempt = (task.getAttemptCount() == null ? 1 : task.getAttemptCount()) + 1;
        String newAttemptId = nextAttemptId(task.getAttemptId());
        Date backoffDeadline = new Date(
                System.currentTimeMillis() + retryBackoffMs + retryBackoff(retryJitterMs));
        boolean claimed = fcTaskRepository.claim(task.getId(), FcTaskStatus.SUBMITTED,
                task.getAttemptId(), new FcTaskRepository.ClaimUpdate(
                        newAttemptId, FcTaskStatus.RETRY_WAIT, backoffDeadline,
                        truncate(errorMessage)));
        if (!claimed) {
            log.info("失败回调认领失败（并发或已流转），忽略 taskId = {}", task.getId());
            return 200;
        }

        if (nextAttempt <= task.getMaxAttempts()) {
            // 退避后重提交由恢复流程执行（RETRY_WAIT + 到期 deadline）
            log.info("任务将重试 taskId = {}, 下一轮 attempt = {}", task.getId(), nextAttempt);
            return 200;
        }

        // 自建额度耗尽
        if ("TRANSCODE".equals(task.getOperation())
                && mpsFallbackService.canFallback(task.getVideoId(), task.getRefId())) {
            log.info("自建重试耗尽，转 MPS 兜底 taskId = {}", task.getId());
            boolean submitted = mpsFallbackService.fallback(task);
            if (submitted) return 200;
        }
        fcTaskRepository.finish(task.getId(), newAttemptId,
                FcTaskStatus.FAILED, new Date(), truncate(errorMessage));
        transcodeCallbackService.onFcTaskFailed(task, truncate(errorMessage));
        return 200;
    }

    private long retryBackoff(long jitterMs) {
        return (long) (Math.random() * jitterMs);
    }

    private String nextAttemptId(String currentAttemptId) {
        return currentAttemptId + "-" + IdUtil.nanoId(6);
    }

    private JSONObject downloadManifest(String key) {
        try (var object = fileService.getObject(key)) {
            if (object == null || object.getObjectContent() == null) return null;
            return JSONObject.parseObject(
                    new String(object.getObjectContent().readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("下载 manifest 失败 key = {}: {}", key, e.getMessage());
            return null;
        }
    }

    private String truncate(String message) {
        if (message == null) return null;
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
