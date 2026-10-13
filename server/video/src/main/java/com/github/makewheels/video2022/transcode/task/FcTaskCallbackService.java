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
import org.springframework.data.mongodb.core.query.Update;

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
        if (FcTaskStatus.isFinal(task.getStatus()) || FcTaskStatus.FALLBACK_PENDING.equals(task.getStatus())) {
            log.info("重复成功回调忽略 taskId = {}, status = {}", taskId, task.getStatus());
            return 200;
        }

        if ("SUCCEEDED".equals(status)) {
            return handleSucceeded(task, body);
        }
        if ("FAILED".equals(status)) {
            if (!FcTaskStatus.SUBMITTED.equals(task.getStatus())) return 200;
            return handleFailed(task, body.getString("errorMessage"),
                    !Boolean.FALSE.equals(body.getBoolean("retryable")));
        }
        log.warn("未知回调 status = {} taskId = {}", status, taskId);
        return 400;
    }

    private int handleSucceeded(FcTask task, JSONObject body) {
        String key = body.getString("outputManifestKey");
        JSONObject payload = task.getPayloadSnapshot();
        String expected = payload == null ? null : payload.getString("outputDir") + "/result.json";
        if (key == null || !key.equals(expected)) return 400;
        if (!fcTaskRepository.beginCompletion(task, key)) return 200;
        task.setStatus(FcTaskStatus.PROCESSING);
        try {
            JSONObject manifest = downloadManifest(key);
            if (!validManifest(task, manifest)) {
                throw new IllegalStateException("manifest 身份与当前任务不符");
            }
            switch (task.getOperation()) {
                case "PROBE" -> {
                    Video video = videoRepository.getById(task.getVideoId());
                    if (video == null) throw new IllegalStateException("视频不存在");
                    transcodeLauncher.onProbeFinished(video, manifest);
                }
                case "TRANSCODE" -> transcodeCallbackService.onSelfHostedTranscodeFinish(task, manifest);
                case "COVER" -> transcodeCallbackService.onFcCoverFinished(task, manifest);
                default -> throw new IllegalArgumentException("未知 operation");
            }
            fcTaskRepository.finish(task.getId(), task.getAttemptId(),
                    FcTaskStatus.SUCCEEDED, new Date(), null);
        } catch (TranscodeCallbackService.RegistrationException e) {
            // 保存的 manifest 和 PROCESSING 租约允许重启后补齐登记；不重新付费编码。
            log.warn("产物登记待恢复 taskId = {}", task.getId());
            return 200;
        } catch (Exception e) {
            return handleFailed(task, "产物处理失败: " + e.getMessage(), true);
        }
        return 200;
    }

    private boolean validManifest(FcTask task, JSONObject manifest) {
        return manifest != null && manifest.getIntValue("schemaVersion") == 1
                && task.getId().equals(manifest.getString("taskId"))
                && task.getAttemptId().equals(manifest.getString("attemptId"))
                && task.getOperation().equals(manifest.getString("operation"));
    }

    private int handleFailed(FcTask task, String errorMessage, boolean retryable) {
        int nextAttempt = (task.getAttemptCount() == null ? 1 : task.getAttemptCount()) + 1;
        if (retryable && nextAttempt <= task.getMaxAttempts()) {
            String newAttemptId = nextAttemptId(task.getAttemptId());
            Date deadline = new Date(System.currentTimeMillis() + retryBackoffMs + retryBackoff(retryJitterMs));
            if (!fcTaskRepository.claim(task.getId(), task.getStatus(), task.getAttemptId(),
                    new FcTaskRepository.ClaimUpdate(newAttemptId, FcTaskStatus.RETRY_WAIT,
                            deadline, truncate(errorMessage)))) return 200;
            task.setAttemptId(newAttemptId);
            task.setAttemptCount(nextAttempt);
            task.setStatus(FcTaskStatus.RETRY_WAIT);
            return 200;
        }
        // 不再执行下一次自建调用：保留最后实际尝试的身份和计数，认领后只能兜底或结束。
        if (!fcTaskRepository.updateCurrent(task, task.getStatus(),
                new Update().set("status", FcTaskStatus.FALLBACK_PENDING)
                        .set("errorMessage", truncate(errorMessage)))) return 200;
        task.setStatus(FcTaskStatus.FALLBACK_PENDING);
        if ("TRANSCODE".equals(task.getOperation())
                && mpsFallbackService.canFallback(task.getVideoId(), task.getRefId())
                && mpsFallbackService.fallback(task)) return 200;
        fcTaskRepository.finish(task.getId(), task.getAttemptId(),
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
