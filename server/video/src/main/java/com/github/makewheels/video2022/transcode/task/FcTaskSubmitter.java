package com.github.makewheels.video2022.transcode.task;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import java.util.Date;

/** 发送前保存完整任务；响应只条件更新，快速回调不会被旧快照覆盖。 */
@Service
@Slf4j
public class FcTaskSubmitter {
    @Resource private CloudFunctionClient cloudFunctionClient;
    @Resource private FcTaskRepository fcTaskRepository;
    @Resource private MongoTemplate mongoTemplate;
    @Resource @Lazy private FcTaskCallbackService callbackService;

    public FcTask createTask(String operation, String videoId, String refId) {
        String id = "fc-" + operation + "-" + (refId == null ? videoId : refId);
        FcTask existing = fcTaskRepository.getById(id);
        if (existing != null) return existing;
        FcTask task = new FcTask();
        task.setId(id);
        task.setOperation(operation);
        task.setVideoId(videoId);
        task.setRefId(refId);
        return task;
    }

    public boolean submit(FcTask task, JSONObject payload) {
        FcTask current = fcTaskRepository.getById(task.getId());
        if (isBlocked(current)) return false;
        long now = System.currentTimeMillis();
        if (task.getExecutionTimeoutMs() == null) {
            task.setExecutionTimeoutMs(Math.max(300_000L,
                    task.getDeadline() == null ? 300_000L : task.getDeadline().getTime() - now));
        }
        if (task.getOutputBaseDir() == null) task.setOutputBaseDir(payload.getString("outputDir"));
        String outputDir = task.getOutputBaseDir() + "/attempts/" + task.getAttemptId();
        payload.put("attemptId", task.getAttemptId());
        payload.put("outputDir", outputDir);
        for (String key : new String[]{"playlistKey", "imageKey"}) {
            String old = payload.getString(key);
            if (old != null) payload.put(key, outputDir + old.substring(old.lastIndexOf('/')));
        }
        task.setPayloadSnapshot(payload);
        task.setDeadline(new Date(now + task.getExecutionTimeoutMs()));
        fcTaskRepository.createIfAbsent(task);
        boolean claimed = fcTaskRepository.updateCurrent(task, FcTaskStatus.CREATED,
                new Update().set("status", FcTaskStatus.SUBMITTED).set("payloadSnapshot", payload)
                        .set("deadline", task.getDeadline()).set("outputBaseDir", task.getOutputBaseDir())
                        .set("executionTimeoutMs", task.getExecutionTimeoutMs()));
        if (!claimed) return false;
        task.setStatus(FcTaskStatus.SUBMITTED);
        if ("TRANSCODE".equals(task.getOperation())) {
            mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(task.getRefId())),
                    new Update().set("m3u8Key", payload.getString("playlistKey")), Transcode.class);
        }
        CloudFunctionClient.SubmitResult result = cloudFunctionClient.submit(task.getProvider(), payload);
        if (result.isAccepted()) {
            fcTaskRepository.updateCurrent(task, FcTaskStatus.SUBMITTED,
                    new Update().set("fcRequestId", result.getRequestId()));
            return true;
        }
        if (result.isThrottled()) {
            int failures = (task.getSubmitFailures() == null ? 0 : task.getSubmitFailures()) + 1;
            if (failures < 30) {
                fcTaskRepository.updateCurrent(task, FcTaskStatus.SUBMITTED,
                        new Update().set("status", FcTaskStatus.RETRY_WAIT)
                                .set("submitFailures", failures)
                                .set("deadline", new Date(now + 60_000L)));
                return false;
            }
        } else if (result.getHttpStatus() == 0 || result.getHttpStatus() >= 500) {
            // 受理不确定：保留执行截止时间等回调，禁止立即重发收费任务。
            log.warn("FC 受理不确定，等待回调或执行截止 taskId = {}", task.getId());
            return false;
        }
        JSONObject failure = new JSONObject();
        failure.put("taskId", task.getId());
        failure.put("attemptId", task.getAttemptId());
        failure.put("status", "FAILED");
        failure.put("retryable", false);
        failure.put("errorMessage", "FC 拒绝提交或排队超时，HTTP " + result.getHttpStatus());
        callbackService.handleCallback(failure);
        return false;
    }
    private boolean isBlocked(FcTask current) {
        return current != null && (FcTaskStatus.isFinal(current.getStatus())
                || FcTaskStatus.SUBMITTED.equals(current.getStatus())
                || FcTaskStatus.PROCESSING.equals(current.getStatus())
                || FcTaskStatus.FALLBACK_PENDING.equals(current.getStatus()));
    }

}
