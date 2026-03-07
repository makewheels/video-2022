package com.github.makewheels.video2022.etc.ding;

import com.alibaba.fastjson.JSONObject;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.github.makewheels.video2022.cover.CoverService;
import com.github.makewheels.video2022.springboot.exception.ExceptionLog;
import com.github.makewheels.video2022.system.context.RequestUtil;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.entity.Watch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private DingService dingService;

    @Mock
    private CoverService coverService;

    @Mock
    private EnvironmentService environmentService;

    @InjectMocks
    private NotificationService notificationService;

    private Video buildVideo(String title, int watchCount) {
        Video video = new Video();
        video.setId("video-test-001");
        video.setTitle(title);
        video.setCoverId("cover-001");
        Watch watch = video.getWatch();
        watch.setWatchCount(watchCount);
        return video;
    }

    private OapiRobotSendResponse successResponse() {
        OapiRobotSendResponse resp = new OapiRobotSendResponse();
        resp.setErrorCode("0");
        resp.setMsg("ok");
        return resp;
    }

    // ========== sendVideoReadyMessage ==========

    @Test
    void sendVideoReadyMessage_includesTitleAndCoverUrl() {
        Video video = buildVideo("精彩视频", 0);
        String coverUrl = "https://oss.example.com/cover/signed.jpg";
        when(coverService.getSignedCoverUrl("cover-001")).thenReturn(coverUrl);
        when(dingService.sendMarkdown(anyString(), anyString(), anyString()))
                .thenReturn(successResponse());

        OapiRobotSendResponse response = notificationService.sendVideoReadyMessage(video);

        assertNotNull(response);

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(dingService).sendMarkdown(
                eq(RobotType.WATCH_LOG), titleCaptor.capture(), textCaptor.capture());

        String title = titleCaptor.getValue();
        String text = textCaptor.getValue();

        assertTrue(title.contains("精彩视频"), "title should contain video title");
        assertTrue(text.contains("精彩视频"), "markdown should contain video title");
        assertTrue(text.contains(coverUrl), "markdown should contain cover URL");
        assertTrue(text.contains(video.getId()), "markdown should contain video id");
    }

    @Test
    void sendVideoReadyMessage_usesWatchLogRobot() {
        Video video = buildVideo("测试", 0);
        when(coverService.getSignedCoverUrl("cover-001")).thenReturn("https://example.com/c.jpg");
        when(dingService.sendMarkdown(anyString(), anyString(), anyString()))
                .thenReturn(successResponse());

        notificationService.sendVideoReadyMessage(video);

        verify(dingService).sendMarkdown(eq(RobotType.WATCH_LOG), anyString(), anyString());
    }

    // ========== sendWatchLogMessage ==========

    @Test
    void sendWatchLogMessage_includesWatchCountAndIpInfo() {
        Video video = buildVideo("教学视频", 42);
        String coverUrl = "https://oss.example.com/cover/watch.jpg";
        when(coverService.getSignedCoverUrl("cover-001")).thenReturn(coverUrl);
        when(dingService.sendMarkdown(anyString(), anyString(), anyString()))
                .thenReturn(successResponse());

        JSONObject ipInfo = new JSONObject();
        ipInfo.put("ip", "1.2.3.4");
        ipInfo.put("province", "北京");
        ipInfo.put("city", "北京");
        ipInfo.put("district", "朝阳区");

        try (MockedStatic<RequestUtil> requestUtil = mockStatic(RequestUtil.class)) {
            requestUtil.when(RequestUtil::getUserAgent).thenReturn("Mozilla/5.0 Test");

            OapiRobotSendResponse response =
                    notificationService.sendWatchLogMessage(video, ipInfo);

            assertNotNull(response);

            ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            verify(dingService).sendMarkdown(
                    eq(RobotType.WATCH_LOG), titleCaptor.capture(), textCaptor.capture());

            String title = titleCaptor.getValue();
            String text = textCaptor.getValue();

            assertTrue(title.contains("教学视频"), "title should contain video title");
            assertTrue(text.contains("42"), "markdown should contain watch count");
            assertTrue(text.contains("1.2.3.4"), "markdown should contain IP");
            assertTrue(text.contains("北京"), "markdown should contain province");
            assertTrue(text.contains("朝阳区"), "markdown should contain district");
            assertTrue(text.contains(coverUrl), "markdown should contain cover URL");
            assertTrue(text.contains("Mozilla/5.0 Test"), "markdown should contain User-Agent");
        }
    }

    @Test
    void sendWatchLogMessage_formatsMessageTitle() {
        Video video = buildVideo("我的视频", 10);
        when(coverService.getSignedCoverUrl("cover-001")).thenReturn("https://example.com/c.jpg");
        when(dingService.sendMarkdown(anyString(), anyString(), anyString()))
                .thenReturn(successResponse());

        JSONObject ipInfo = new JSONObject();
        ipInfo.put("ip", "10.0.0.1");
        ipInfo.put("province", "上海");
        ipInfo.put("city", "上海");
        ipInfo.put("district", "浦东");

        try (MockedStatic<RequestUtil> requestUtil = mockStatic(RequestUtil.class)) {
            requestUtil.when(RequestUtil::getUserAgent).thenReturn("TestAgent");
            notificationService.sendWatchLogMessage(video, ipInfo);
        }

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        verify(dingService).sendMarkdown(
                eq(RobotType.WATCH_LOG), titleCaptor.capture(), anyString());
        assertTrue(titleCaptor.getValue().contains("观看记录"));
        assertTrue(titleCaptor.getValue().contains("我的视频"));
    }

    // ========== sendExceptionMessage ==========

    @Test
    void sendExceptionMessage_includesExceptionMessageAndLogLink() {
        Exception exception = new RuntimeException("空指针异常");
        ExceptionLog log = new ExceptionLog();
        log.setId("exception-log-001");
        log.setExceptionStackTrace("java.lang.NullPointerException\n\tat com.example.Foo.bar(Foo.java:42)");

        String baseUrl = "https://api.example.com";
        when(environmentService.getCallbackUrl(anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0);
            return baseUrl + path;
        });

        notificationService.sendExceptionMessage(exception, log);

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(dingService).sendMarkdown(
                eq(RobotType.EXCEPTION), titleCaptor.capture(), textCaptor.capture());

        String title = titleCaptor.getValue();
        String text = textCaptor.getValue();

        assertEquals("异常信息", title);
        assertTrue(text.contains("空指针异常"), "markdown should contain exception message");
        assertNotNull(log.getId(), "ExceptionLog id should not be null");
        assertTrue(text.contains(log.getId()), "markdown should contain exception log id");
        assertTrue(text.contains(baseUrl), "markdown should contain callback base URL");
        assertTrue(text.contains("exceptionLogId="), "markdown should contain log id param");
        assertTrue(text.contains("NullPointerException"), "markdown should contain stack trace");
    }

    @Test
    void sendExceptionMessage_usesExceptionRobot() {
        Exception exception = new RuntimeException("test error");
        ExceptionLog log = new ExceptionLog();
        log.setId("exc-log-002");
        log.setExceptionStackTrace("stack trace here");

        when(environmentService.getCallbackUrl(anyString())).thenReturn("https://example.com/log");

        notificationService.sendExceptionMessage(exception, log);

        verify(dingService).sendMarkdown(eq(RobotType.EXCEPTION), anyString(), anyString());
    }

    @Test
    void sendExceptionMessage_truncatesLongStackTrace() {
        Exception exception = new RuntimeException("error");
        ExceptionLog log = new ExceptionLog();
        log.setId("exc-log-003");
        // Create a stack trace longer than 500 chars
        String longTrace = "x".repeat(1000);
        log.setExceptionStackTrace(longTrace);

        when(environmentService.getCallbackUrl(anyString())).thenReturn("https://example.com/log");

        notificationService.sendExceptionMessage(exception, log);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(dingService).sendMarkdown(eq(RobotType.EXCEPTION), anyString(), textCaptor.capture());

        String text = textCaptor.getValue();
        // The original stack trace is 1000 chars but should be truncated to 500
        assertFalse(text.contains(longTrace),
                "full 1000-char trace should not appear; it should be truncated");
    }
}
