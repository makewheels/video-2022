package com.github.makewheels.video2022.openapi.webhook;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.openapi.webhook.service.WebhookDispatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * Webhook 事件发布器，供其他 service 调用
 */
@Component
@Slf4j
public class WebhookEventPublisher {
    @Resource
    private WebhookDispatchService webhookDispatchService;

    /**
     * 视频上传完成事件
     */
    public void publishVideoUploadCompleted(String videoId, String userId) {
        log.info("Publishing webhook event: video.upload.completed, videoId={}, userId={}",
                videoId, userId);

        JSONObject data = new JSONObject();
        data.put("videoId", videoId);
        data.put("userId", userId);

        // TODO 通过 userId 查找关联的 appId，当前先记录日志
        log.info("Webhook event published: video.upload.completed (appId mapping pending)");
    }

    /**
     * 转码完成事件
     */
    public void publishTranscodeCompleted(String videoId, String transcodeId, String resolution) {
        log.info("Publishing webhook event: video.transcode.completed, videoId={}, transcodeId={}, resolution={}",
                videoId, transcodeId, resolution);

        JSONObject data = new JSONObject();
        data.put("videoId", videoId);
        data.put("transcodeId", transcodeId);
        data.put("resolution", resolution);

        // TODO 通过 videoId 查找关联的 appId，当前先记录日志
        log.info("Webhook event published: video.transcode.completed (appId mapping pending)");
    }

    /**
     * 转码失败事件
     */
    public void publishTranscodeFailed(String videoId, String transcodeId) {
        log.info("Publishing webhook event: video.transcode.failed, videoId={}, transcodeId={}",
                videoId, transcodeId);

        JSONObject data = new JSONObject();
        data.put("videoId", videoId);
        data.put("transcodeId", transcodeId);

        // TODO 通过 videoId 查找关联的 appId，当前先记录日志
        log.info("Webhook event published: video.transcode.failed (appId mapping pending)");
    }

    /**
     * 视频删除事件
     */
    public void publishVideoDeleted(String videoId) {
        log.info("Publishing webhook event: video.deleted, videoId={}", videoId);

        JSONObject data = new JSONObject();
        data.put("videoId", videoId);

        // TODO 通过 videoId 查找关联的 appId，当前先记录日志
        log.info("Webhook event published: video.deleted (appId mapping pending)");
    }
}
