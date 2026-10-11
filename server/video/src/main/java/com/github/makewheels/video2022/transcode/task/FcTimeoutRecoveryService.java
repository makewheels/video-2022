package com.github.makewheels.video2022.transcode.task;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.TranscodeCallbackService;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.contants.TranscodeStatus;
import com.github.makewheels.video2022.transcode.mps.MpsFallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 自建任务超时/失败恢复：扫描 RETRY_WAIT 与 SUBMITTED 超时任务，
 * 原子认领后重提交（同一 payload、新 attemptId），额度耗尽走 MPS 兜底或终态失败。
 * 服务重启后同样由本流程恢复，不依赖 JVM 内存状态。
 */
@Service
@Slf4j
public class FcTimeoutRecoveryService {
    @Resource
    private FcTaskRepository fcTaskRepository;
    @Resource
    private FcTaskSubmitter fcTaskSubmitter;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private TranscodeCallbackService transcodeCallbackService;
    @Resource
    private MpsFallbackService mpsFallbackService;

    @Value("${transcode.recovery.enabled:true}")
    private boolean recoveryEnabled;

    /**
     * 每分钟扫描一次。
     */
    @Scheduled(fixedDelay = 60_000)
    public void recover() {
        if (!recoveryEnabled) return;
        Date now = new Date();
        List<FcTask> tasks = fcTaskRepository.findTimeoutUnfinished(now);
        for (FcTask task : tasks) {
            try {
                recoverOne(task, now);
            } catch (Exception e) {
                log.error("恢复任务异常 taskId = {}: {}", task.getId(), e.getMessage());
            }
        }
    }

    private void recoverOne(FcTask task, Date now) {
        int attemptCount = task.getAttemptCount() == null ? 1 : task.getAttemptCount();
        int nextAttempt = attemptCount + 1;
        String newAttemptId = task.getAttemptId() + "-a" + nextAttempt;

        // 原子认领：SUBMITTED 超时 → RETRY_WAIT，attemptCount+1（这一轮执行未等到结果，计一次消耗）
        boolean claimed = fcTaskRepository.claim(task.getId(), FcTaskStatus.SUBMITTED,
                task.getAttemptId(), new FcTaskRepository.ClaimUpdate(
                        newAttemptId, FcTaskStatus.RETRY_WAIT,
                        new Date(now.getTime() + 300_000L), "执行超时"));
        if (!claimed) return;

        if (attemptCount < task.getMaxAttempts()) {
            resubmit(task, newAttemptId, nextAttempt);
            return;
        }

        // 额度耗尽：TRANSCODE 尝试 MPS 兜底，否则终态失败
        if ("TRANSCODE".equals(task.getOperation())
                && mpsFallbackService.canFallback(task.getVideoId(), task.getRefId())) {
            log.info("恢复流程：自建额度耗尽，转 MPS 兜底 taskId = {}", task.getId());
            if (mpsFallbackService.fallback(task)) return;
        }
        fcTaskRepository.finish(task.getId(), newAttemptId,
                FcTaskStatus.FAILED, new Date(), "自建执行超时且无可兜底路径");
        transcodeCallbackService.onFcTaskFailed(task, "自建执行超时");
    }

    /**
     * 用快照 payload 重提交（新 attemptId）；提交未受理时保持 RETRY_WAIT 等待下一轮
     */
    private void resubmit(FcTask task, String newAttemptId, int newAttemptCount) {
        JSONObject payload = task.getPayloadSnapshot();
        if (payload == null) {
            log.error("任务缺少 payload 快照，无法重提交 taskId = {}", task.getId());
            fcTaskRepository.finish(task.getId(), newAttemptId,
                    FcTaskStatus.FAILED, new Date(), "缺少 payload 快照");
            transcodeCallbackService.onFcTaskFailed(task, "缺少 payload 快照");
            return;
        }
        task.setAttemptId(newAttemptId);
        task.setAttemptCount(newAttemptCount);
        payload.put("attemptId", newAttemptId);
        log.info("恢复流程重提交 taskId = {}, attempt = {}", task.getId(), newAttemptCount);
        fcTaskSubmitter.submit(task, payload);
    }
}
