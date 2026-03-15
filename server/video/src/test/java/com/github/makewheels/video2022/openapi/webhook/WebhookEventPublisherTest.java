package com.github.makewheels.video2022.openapi.webhook;

import com.github.makewheels.video2022.openapi.webhook.service.WebhookDispatchService;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WebhookEventPublisher 单元测试。
 * 验证事件派发逻辑：有 apiAppId 时派发，无 apiAppId 时跳过。
 */
@ExtendWith(MockitoExtension.class)
class WebhookEventPublisherTest {

    @Mock
    private WebhookDispatchService webhookDispatchService;

    @Mock
    private VideoRepository videoRepository;

    @InjectMocks
    private WebhookEventPublisher webhookEventPublisher;

    private Video apiVideo;
    private Video normalVideo;

    @BeforeEach
    void setUp() {
        // API 创建的视频 — 有 apiAppId
        apiVideo = new Video();
        apiVideo.setId("video-api-001");
        apiVideo.setUploaderId("user-001");
        apiVideo.setApiAppId("app-001");

        // 普通上传的视频 — 无 apiAppId
        normalVideo = new Video();
        normalVideo.setId("video-normal-001");
        normalVideo.setUploaderId("user-002");
        // apiAppId 为 null
    }

    // ==========================================
    // publishVideoUploadCompleted
    // ==========================================

    @Test
    void publishVideoUploadCompleted_withApiVideo_shouldDispatch() {
        when(videoRepository.getById("video-api-001")).thenReturn(apiVideo);

        webhookEventPublisher.publishVideoUploadCompleted("video-api-001", "user-001");

        verify(webhookDispatchService).dispatchEvent(
                eq("app-001"), eq("video.upload.completed"), argThat(data -> {
                    String json = data.toString();
                    return json.contains("video-api-001") && json.contains("user-001");
                }));
    }

    @Test
    void publishVideoUploadCompleted_withNormalVideo_shouldSkip() {
        when(videoRepository.getById("video-normal-001")).thenReturn(normalVideo);

        webhookEventPublisher.publishVideoUploadCompleted("video-normal-001", "user-002");

        verify(webhookDispatchService, never()).dispatchEvent(any(), any(), any());
    }

    @Test
    void publishVideoUploadCompleted_videoNotFound_shouldSkip() {
        when(videoRepository.getById("nonexistent")).thenReturn(null);

        webhookEventPublisher.publishVideoUploadCompleted("nonexistent", "user-001");

        verify(webhookDispatchService, never()).dispatchEvent(any(), any(), any());
    }

    // ==========================================
    // publishTranscodeCompleted
    // ==========================================

    @Test
    void publishTranscodeCompleted_withApiVideo_shouldDispatch() {
        when(videoRepository.getById("video-api-001")).thenReturn(apiVideo);

        webhookEventPublisher.publishTranscodeCompleted("video-api-001", "transcode-001", "1080p");

        verify(webhookDispatchService).dispatchEvent(
                eq("app-001"), eq("video.transcode.completed"), argThat(data -> {
                    String json = data.toString();
                    return json.contains("video-api-001")
                            && json.contains("transcode-001")
                            && json.contains("1080p");
                }));
    }

    @Test
    void publishTranscodeCompleted_withNormalVideo_shouldSkip() {
        when(videoRepository.getById("video-normal-001")).thenReturn(normalVideo);

        webhookEventPublisher.publishTranscodeCompleted("video-normal-001", "t-001", "720p");

        verify(webhookDispatchService, never()).dispatchEvent(any(), any(), any());
    }

    // ==========================================
    // publishTranscodeFailed
    // ==========================================

    @Test
    void publishTranscodeFailed_withApiVideo_shouldDispatch() {
        when(videoRepository.getById("video-api-001")).thenReturn(apiVideo);

        webhookEventPublisher.publishTranscodeFailed("video-api-001", "transcode-fail-001");

        verify(webhookDispatchService).dispatchEvent(
                eq("app-001"), eq("video.transcode.failed"), argThat(data -> {
                    String json = data.toString();
                    return json.contains("video-api-001") && json.contains("transcode-fail-001");
                }));
    }

    @Test
    void publishTranscodeFailed_withNormalVideo_shouldSkip() {
        when(videoRepository.getById("video-normal-001")).thenReturn(normalVideo);

        webhookEventPublisher.publishTranscodeFailed("video-normal-001", "t-001");

        verify(webhookDispatchService, never()).dispatchEvent(any(), any(), any());
    }

    // ==========================================
    // publishVideoDeleted
    // ==========================================

    @Test
    void publishVideoDeleted_withApiVideo_shouldDispatch() {
        when(videoRepository.getById("video-api-001")).thenReturn(apiVideo);

        webhookEventPublisher.publishVideoDeleted("video-api-001");

        verify(webhookDispatchService).dispatchEvent(
                eq("app-001"), eq("video.deleted"), argThat(data -> {
                    String json = data.toString();
                    return json.contains("video-api-001");
                }));
    }

    @Test
    void publishVideoDeleted_withNormalVideo_shouldSkip() {
        when(videoRepository.getById("video-normal-001")).thenReturn(normalVideo);

        webhookEventPublisher.publishVideoDeleted("video-normal-001");

        verify(webhookDispatchService, never()).dispatchEvent(any(), any(), any());
    }

    @Test
    void publishVideoDeleted_videoNotFound_shouldSkip() {
        when(videoRepository.getById("nonexistent")).thenReturn(null);

        webhookEventPublisher.publishVideoDeleted("nonexistent");

        verify(webhookDispatchService, never()).dispatchEvent(any(), any(), any());
    }
}
