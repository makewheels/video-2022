package com.github.makewheels.video2022.video;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.entity.Watch;
import com.github.makewheels.video2022.video.bean.entity.YouTube;
import com.github.makewheels.video2022.video.service.YoutubeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class YoutubeServiceTest {

    @Mock
    private EnvironmentService environmentService;

    @InjectMocks
    private YoutubeService youtubeService;

    // --- Pure logic: getFileExtension ---

    @Test
    void getFileExtension_shouldAlwaysReturnWebm() {
        assertEquals("webm", youtubeService.getFileExtension("dQw4w9WgXcQ"));
    }

    @Test
    void getFileExtension_nullId_shouldReturnWebm() {
        assertEquals("webm", youtubeService.getFileExtension(null));
    }

    // --- Pure logic: getYoutubeVideoIdByUrl ---

    @ParameterizedTest
    @CsvSource({
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ, dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=XRWFWB2BP_s&list=PLAhTBeRe8IhM, XRWFWB2BP_s",
            "https://www.youtube.com/watch?v=abc123&t=120&index=5, abc123",
            "https://youtu.be/tci5eYHwjMc, tci5eYHwjMc",
            "https://youtu.be/tci5eYHwjMc?t=72, tci5eYHwjMc"
    })
    void getYoutubeVideoIdByUrl_validUrls(String url, String expectedId) {
        assertEquals(expectedId, youtubeService.getYoutubeVideoIdByUrl(url));
    }

    @Test
    void getYoutubeVideoIdByUrl_invalidUrl_shouldReturnNull() {
        assertNull(youtubeService.getYoutubeVideoIdByUrl("not a valid url %%%"));
    }

    @Test
    void getYoutubeVideoIdByUrl_unknownHost_shouldReturnNull() {
        assertNull(youtubeService.getYoutubeVideoIdByUrl("https://vimeo.com/123456"));
    }

    // --- API: getVideoInfo ---

    @Test
    void getVideoInfo_shouldCallServiceAndParseJson() {
        when(environmentService.getYoutubeServiceUrl()).thenReturn("https://yt-service.example.com");

        JSONObject expected = new JSONObject();
        expected.put("title", "Test Video");
        expected.put("duration", 120);

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            httpUtilMock.when(() -> HttpUtil.get(
                    "https://yt-service.example.com/youtube/getVideoInfo?youtubeVideoId=abc123"
            )).thenReturn(expected.toJSONString());

            JSONObject result = youtubeService.getVideoInfo("abc123");

            assertNotNull(result);
            assertEquals("Test Video", result.getString("title"));
            assertEquals(120, result.getIntValue("duration"));
        }
    }

    // --- API: transferVideo ---

    @Test
    void transferVideo_shouldBuildBodyAndCallApi() {
        when(environmentService.getYoutubeServiceUrl()).thenReturn("https://yt-service.example.com");
        when(environmentService.getCallbackUrl(anyString())).thenAnswer(inv ->
                "https://callback.example.com" + inv.getArgument(0, String.class));

        User user = new User();
        user.setId("user123");
        user.setToken("token456");

        Video video = new Video();
        video.setId("video789");
        video.getYouTube().setVideoId("ytVid001");
        video.getWatch().setWatchId("watch001");

        File file = new File();
        file.setId("file111");
        file.setKey("videos/raw/file111.mp4");
        file.setProvider("ALIYUN_OSS");

        JSONObject apiResponse = new JSONObject();
        apiResponse.put("requestId", "req-001");

        HttpRequest mockRequest = mock(HttpRequest.class);
        HttpResponse mockResponse = mock(HttpResponse.class);
        when(mockRequest.header(anyString(), anyString())).thenReturn(mockRequest);
        doReturn(mockRequest).when(mockRequest).body(anyString());
        when(mockRequest.execute()).thenReturn(mockResponse);
        doReturn(apiResponse.toJSONString()).when(mockResponse).body();

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            httpUtilMock.when(() -> HttpUtil.createPost(
                    "https://yt-service.example.com/youtube/transferVideo"
            )).thenReturn(mockRequest);

            JSONObject result = youtubeService.transferVideo(user, video, file);

            assertNotNull(result);
            assertEquals("req-001", result.getString("requestId"));

            // Verify async header
            verify(mockRequest).header("X-Fc-Invocation-Type", "Async");

            // Verify body contains required fields
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockRequest).body(bodyCaptor.capture());
            JSONObject sentBody = JSONObject.parseObject(bodyCaptor.getValue());
            assertEquals("ytVid001", sentBody.getString("youtubeVideoId"));
            assertEquals("videos/raw/file111.mp4", sentBody.getString("key"));
            assertEquals("ALIYUN_OSS", sentBody.getString("provider"));
            assertEquals("file111", sentBody.getString("fileId"));
            assertEquals("watch001", sentBody.getString("watchId"));
            assertEquals("video789", sentBody.getString("videoId"));
            assertNotNull(sentBody.getString("missionId"));
            assertTrue(sentBody.getString("getUploadCredentialsUrl")
                    .contains("fileId=file111"));
            assertTrue(sentBody.getString("fileUploadFinishCallbackUrl")
                    .contains("fileId=file111"));
            assertTrue(sentBody.getString("businessUploadFinishCallbackUrl")
                    .contains("videoId=video789"));
        }
    }

    // --- API: transferFile ---

    @Test
    void transferFile_shouldBuildBodyWithDownloadUrl() {
        when(environmentService.getYoutubeServiceUrl()).thenReturn("https://yt-service.example.com");
        when(environmentService.getCallbackUrl(anyString())).thenAnswer(inv ->
                "https://callback.example.com" + inv.getArgument(0, String.class));

        User user = new User();
        user.setId("user123");
        user.setToken("token456");

        File file = new File();
        file.setId("file222");
        file.setKey("videos/raw/file222.mp4");
        file.setProvider("ALIYUN_OSS");

        JSONObject apiResponse = new JSONObject();
        apiResponse.put("status", "ok");

        HttpRequest mockRequest = mock(HttpRequest.class);
        HttpResponse mockResponse = mock(HttpResponse.class);
        when(mockRequest.header(anyString(), anyString())).thenReturn(mockRequest);
        doReturn(mockRequest).when(mockRequest).body(anyString());
        when(mockRequest.execute()).thenReturn(mockResponse);
        doReturn(apiResponse.toJSONString()).when(mockResponse).body();

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            httpUtilMock.when(() -> HttpUtil.createPost(
                    "https://yt-service.example.com/youtube/transferFile"
            )).thenReturn(mockRequest);

            JSONObject result = youtubeService.transferFile(
                    user, file, "https://cdn.example.com/download.mp4",
                    "https://callback.example.com/business/finish");

            assertNotNull(result);
            assertEquals("ok", result.getString("status"));

            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockRequest).body(bodyCaptor.capture());
            JSONObject sentBody = JSONObject.parseObject(bodyCaptor.getValue());
            assertEquals("https://cdn.example.com/download.mp4",
                    sentBody.getString("downloadUrl"));
            assertEquals("file222", sentBody.getString("fileId"));
            assertEquals("https://callback.example.com/business/finish",
                    sentBody.getString("businessUploadFinishCallbackUrl"));
        }
    }
}
