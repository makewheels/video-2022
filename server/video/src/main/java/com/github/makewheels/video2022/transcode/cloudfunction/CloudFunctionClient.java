package com.github.makewheels.video2022.transcode.cloudfunction;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.task.FcTask;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 自建云函数提交客户端：异步 POST 新协议任务，返回 FC 受理结果。
 * URL 与调用密钥全部走配置，未配置则抛出异常禁止提交。
 */
@Service
@Slf4j
public class CloudFunctionClient {
    @Value("${aliyun.cf.transcode.cpu.url:}")
    private String cpuFunctionUrl;
    @Value("${aliyun.cf.transcode.gpu.url:}")
    private String gpuFunctionUrl;
    @Value("${aliyun.cf.transcode.invoke-secret:}")
    private String invokeSecret;
    @Value("${aliyun.oss.video.bucket}")
    private String bucket;
    @Value("${aliyun.oss.video.internal-endpoint}")
    private String internalEndpoint;

    @Data
    public static class SubmitResult {
        /** 2xx = FC 受理（异步排队） */
        private boolean accepted;
        /** 429 = 平台限流/排队，可短暂退避后原样重试 */
        private boolean throttled;
        private int httpStatus;
        private String requestId;
    }

    /**
     * 提交任务。provider 决定 CPU / GPU 函数地址。
     */
    public SubmitResult submit(String provider, JSONObject payload) {
        String url = getFunctionUrl(provider);
        String taskId = payload.getString("taskId");
        HttpRequest post = HttpUtil.createPost(url);
        post.header("X-Fc-Invocation-Type", "Async");
        post.header("X-Invoke-Secret", invokeSecret);
        post.body(payload.toJSONString());
        log.info("提交自建云函数任务 provider = {}, taskId = {}, attemptId = {}",
                provider, taskId, payload.getString("attemptId"));
        SubmitResult result = new SubmitResult();
        try (HttpResponse response = post.execute()) {
            result.setHttpStatus(response.getStatus());
            result.setRequestId(response.header("X-Fc-Request-Id"));
            result.setAccepted(response.getStatus() >= 200 && response.getStatus() < 300);
            result.setThrottled(response.getStatus() == 429);
        } catch (Exception e) {
            // 网络异常：受理结果不确定，按未受理返回，由上层走恢复流程，不盲目重复提交
            log.warn("自建云函数提交异常 provider = {}, taskId = {}: {}", provider, taskId, e.getMessage());
            result.setAccepted(false);
            result.setThrottled(false);
        }
        return result;
    }

    public String getFunctionUrl(String provider) {
        String url;
        switch (provider) {
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU:
                url = cpuFunctionUrl;
                break;
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU:
                url = gpuFunctionUrl;
                break;
            default:
                throw new IllegalArgumentException("不支持自建函数提交的 provider = " + provider);
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("自建云函数 URL 未配置，provider = " + provider
                    + "，请检查 aliyun.cf.transcode.* 配置");
        }
        return url;
    }

    public boolean isConfigured(String provider) {
        try {
            getFunctionUrl(provider);
            return invokeSecret != null && !invokeSecret.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 协议 v1 公共字段。bucket/endpoint 由本客户端配置注入；任务身份来自 FcTask。
     */
    public JSONObject buildPayload(FcTask task, String transcodeId, String inputKey,
                                   String outputDir, String callbackUrl) {
        JSONObject payload = new JSONObject();
        payload.put("schemaVersion", 1);
        payload.put("taskId", task.getId());
        payload.put("attemptId", task.getAttemptId());
        payload.put("operation", task.getOperation());
        payload.put("videoId", task.getVideoId());
        payload.put("transcodeId", transcodeId);
        payload.put("bucket", bucket);
        payload.put("endpoint", internalEndpoint);
        payload.put("inputKey", inputKey);
        payload.put("outputDir", outputDir);
        payload.put("callbackUrl", callbackUrl);
        return payload;
    }

    /**
     * 日志输出（不含 invoke secret）
     */
    public String safeLog(JSONObject payload) {
        return JSON.toJSONString(payload);
    }
}
