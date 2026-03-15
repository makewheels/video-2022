package com.github.makewheels.video2022.openapi.webhook;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.openapi.webhook.service.WebhookDispatchService;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * Webhook 事件发布器，供其他 service 调用。
 * 只有通过 API 创建的视频（有 apiAppId）才会触发 webhook 派发。
 */
@Component
@Slf4j
public class WebhookEventPublisher {
    @Resource
    private WebhookDispatchService webhookDispatchService;
    @Resource
    private VideoRepository videoRepository;

    /**
     * 根据 videoId 查找关联的 appId
     */
    private String resolveAppId(String videoId) {
        Video video = videoRepository.getById(videoId);
        if (video == null) {
            log.warn("Webhook: 视频不存在, videoId={}", videoId);
            return null;
        }
        return video.getApiAppId();
    }

    /**
     * 视频就绪事件（上传 + 转码完成后触发）
     */
    public void publishVideoUploadCompleted(String videoId, String userId) {
        log.info("Publishing webhook event: video.upload.completed, videoId={}, userId={}",
                videoId, userId);

        String appId = resolveAppId(videoId);
        if (StringUtils.isBlank(appId)) {
            log.debug("Webhook: 非 API 视频，跳过派发, videoId={}", videoId);
            return;
        }

        JSONObject data = new JSONObject();
        data.put("videoId", videoId);
        data.put("userId", userId);

        webhookDispatchService.dispatchEvent(appId, "video.upload.completed", data);
    }

    /**
     * 转码完成事件
     */
    public void publishTranscodeCompleted(String videoId, String transcodeId, String resolution) {
        log.info("Publishing webhook event: video.transcode.completed, videoId={}, transcodeId={}, resolution={}",
                videoId, transcodeId, resolution);

        String appId = resolveAppId(videoId);
        if (StringUtils.isBlank(appId)) {
            log.debug("Webhook: 非 API 视频，跳过派发, videoId={}", videoId);
            return;
        }

        JSONObject data = new JSONObject();
        data.put("videoId", videoId);
        data.put("transcodeId", transcodeId);
        data.put("resolution", resolution);

        webhookDispatchService.dispatchEvent(appId, "video.transcode.completed", data);
    }

    /**
     * 转码失败事件
     */
    public void publishTranscodeFailed(String videoId, String transcodeId) {
        log.info("Publishing webhook event: video.transcode.failed, videoId={}, transcodeId={}",
                videoId, transcodeId);

        String appId = resolveAppId(videoId);
        if (StringUtils.isBlank(appId)) {
            log.debug("Webhook: 非 API 视频，跳过派发, videoId={}", videoId);
            return;
        }

        JSONObject data = new JSONObject();
        data.put("videoId", videoId);
        data.put("transcodeId", transcodeId);

        webhookDispatchService.dispatchEvent(appId, "video.transcode.failed", data);
    }

    /**
     * 视频删除事件
     */
    public void publishVideoDeleted(String videoId) {
        log.info("Publishing webhook event: video.deleted, videoId={}", videoId);

        String appId = resolveAppId(videoId);
        if (StringUtils.isBlank(appId)) {
            log.debug("Webhook: 非 API 视频，跳过派发, videoId={}", videoId);
            return;
        }

        JSONObject data = new JSONObject();
        data.put("videoId", videoId);

        webhookDispatchService.dispatchEvent(appId, "video.deleted", data);
    }
}
