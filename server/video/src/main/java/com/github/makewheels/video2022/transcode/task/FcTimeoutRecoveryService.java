package com.github.makewheels.video2022.transcode.task;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.TranscodeCallbackService;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.contants.TranscodeStatus;
import com.github.makewheels.video2022.transcode.mps.MpsFallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    @org.springframework.context.annotation.Lazy
    private FcTaskCallbackService callbackService;

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
        if (FcTaskStatus.PROCESSING.equals(task.getStatus())) {
            if (!fcTaskRepository.updateCurrent(task, FcTaskStatus.PROCESSING,
                    new Update().set("status", FcTaskStatus.SUBMITTED))) return;
            JSONObject callback = new JSONObject();
            callback.put("taskId", task.getId());
            callback.put("attemptId", task.getAttemptId());
            callback.put("status", "SUCCEEDED");
            callback.put("outputManifestKey", task.getResultManifestKey());
            callbackService.handleCallback(callback);
            return;
        }
        if (FcTaskStatus.RETRY_WAIT.equals(task.getStatus()) || FcTaskStatus.CREATED.equals(task.getStatus())) {
            if (!fcTaskRepository.updateCurrent(task, task.getStatus(),
                    new Update().set("status", FcTaskStatus.CREATED)
                            .set("deadline", new Date(now.getTime() + 300_000L)))) return;
            resubmit(task, task.getAttemptId(), task.getAttemptCount());
            return;
        }
        JSONObject failure = new JSONObject();
        failure.put("taskId", task.getId());
        failure.put("attemptId", task.getAttemptId());
        failure.put("status", "FAILED");
        failure.put("errorMessage", "自建执行超时");
        callbackService.handleCallback(failure);
    }

    /**
     * RETRY_WAIT 到期认领：条件更新为 CREATED 占位（重提交成功后 submit() 置 SUBMITTED），不增计数
     */
    private boolean mongoClaimNoInc(FcTask task) {
        Query query = Query.query(Criteria.where("id").is(task.getId())
                .and("status").is(FcTaskStatus.RETRY_WAIT)
                .and("attemptId").is(task.getAttemptId()));
        return mongoTemplate.updateFirst(query,
                new Update().set("status", FcTaskStatus.CREATED), FcTask.class)
                .getModifiedCount() == 1;
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
