package com.github.makewheels.video2022.transcode.task;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;

/**
 * 自建函数任务提交：生成任务与 attempt、异步提交、处理受理响应。
 * <p>
 * 提交失败（429 / 5xx / 网络异常）不计 attempt：置 RETRY_WAIT 并设短 deadline，
 * 由恢复流程以同一 attemptId 重新提交（同 attemptId 下函数重复执行幂等覆盖同目录）。
 */
@Service
@Slf4j
public class FcTaskSubmitter {
    @Resource
    private CloudFunctionClient cloudFunctionClient;
    @Resource
    private FcTaskRepository fcTaskRepository;

    @Value("${transcode.submit.retry-delays-ms:2000,5000,10000}")
    private String retryDelaysMs;

    /**
     * 创建任务记录（未提交）。调用方补齐 payload 业务字段后调 {@link #submit(FcTask, JSONObject)}。
     */
    public FcTask createTask(String operation, String videoId, String refId) {
        FcTask task = new FcTask();
        task.setId("fc-" + IdUtil.getSnowflakeNextIdStr());
        task.setOperation(operation);
        task.setVideoId(videoId);
        task.setRefId(refId);
        fcTaskRepository.save(task);
        return task;
    }

    /**
     * 提交任务。带退避重试（仅限 429），全部失败置 RETRY_WAIT（不耗 attempt）。
     * 提交前把 payload 存入任务快照，供失败重试与超时恢复重提交。
     *
     * @return true = 已受理；false = 暂未受理（走恢复流程）
     */
    public boolean submit(FcTask task, JSONObject payload) {
        task.setPayloadSnapshot(payload);
        String[] delays = retryDelaysMs.split(",");
        CloudFunctionClient.SubmitResult result = null;
        for (int i = 0; i <= delays.length; i++) {
            if (i > 0) {
                ThreadUtil.sleep(Long.parseLong(delays[i - 1].trim()));
                log.info("云函数提交退避重试 taskId = {}, 第 {} 次", task.getId(), i);
            }
            result = cloudFunctionClient.submit(task.getProvider(), payload);
            if (result.isAccepted() || !result.isThrottled()) break;
        }

        Date now = new Date();
        if (result.isAccepted()) {
            task.setStatus(FcTaskStatus.SUBMITTED);
            task.setFcRequestId(result.getRequestId());
            fcTaskRepository.save(task);
            log.info("云函数任务受理 taskId = {}, attemptId = {}, fcRequestId = {}",
                    task.getId(), task.getAttemptId(), result.getRequestId());
            return true;
        }
        // 未受理：RETRY_WAIT + 短 deadline，恢复流程重新提交（同一 attemptId）
        task.setStatus(FcTaskStatus.RETRY_WAIT);
        task.setDeadline(new Date(now.getTime() + 60_000L));
        fcTaskRepository.save(task);
        log.warn("云函数任务未受理 taskId = {}, httpStatus = {}, 置 RETRY_WAIT",
                task.getId(), result.getHttpStatus());
        return false;
    }
}
