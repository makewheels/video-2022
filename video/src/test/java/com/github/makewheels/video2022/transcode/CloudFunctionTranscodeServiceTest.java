package com.github.makewheels.video2022.transcode;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionTranscodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudFunctionTranscodeServiceTest {

    @InjectMocks
    private CloudFunctionTranscodeService cloudFunctionTranscodeService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cloudFunctionTranscodeService,
                "bucket", "test-video-bucket");
        ReflectionTestUtils.setField(cloudFunctionTranscodeService,
                "endpoint", "oss-cn-beijing-internal.aliyuncs.com");
    }

    @Test
    void transcode_shouldBuildRequestAndCallCloudFunction() {
        HttpRequest mockRequest = mock(HttpRequest.class);
        HttpResponse mockResponse = mock(HttpResponse.class);
        doReturn(mockRequest).when(mockRequest).body(anyString());
        when(mockRequest.header(anyString(), anyString())).thenReturn(mockRequest);
        when(mockRequest.execute()).thenReturn(mockResponse);
        when(mockResponse.header("X-Fc-Request-Id")).thenReturn("fc-req-001");
        doReturn("").when(mockResponse).body();

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            httpUtilMock.when(() -> HttpUtil.createPost(
                    "https://transcoe-master-video-transcode-pqrshwejna.cn-beijing.fcapp.run"
            )).thenReturn(mockRequest);

            String result = cloudFunctionTranscodeService.transcode(
                    "videos/raw/input.mp4",
                    "videos/transcode/output/",
                    "video001", "transcode001", "job001",
                    "720p", 1280, 720,
                    "h264", "aac", "high",
                    "https://callback.example.com/transcode/finish"
            );

            assertNotNull(result);

            // Verify async header
            verify(mockRequest).header("X-Fc-Invocation-Type", "Async");

            // Verify request body content
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockRequest).body(bodyCaptor.capture());

            JSONObject body = JSONObject.parseObject(bodyCaptor.getValue());
            assertEquals("test-video-bucket", body.getString("bucket"));
            assertEquals("oss-cn-beijing-internal.aliyuncs.com", body.getString("endpoint"));
            assertEquals("videos/raw/input.mp4", body.getString("inputKey"));
            assertEquals("videos/transcode/output/", body.getString("outputDir"));
            assertEquals("video001", body.getString("videoId"));
            assertEquals("transcode001", body.getString("transcodeId"));
            assertEquals("job001", body.getString("jobId"));
            assertEquals("720p", body.getString("resolution"));
            assertEquals(1280, body.getIntValue("width"));
            assertEquals(720, body.getIntValue("height"));
            assertEquals("h264", body.getString("videoCodec"));
            assertEquals("aac", body.getString("audioCodec"));
            assertEquals("high", body.getString("quality"));
            assertEquals("https://callback.example.com/transcode/finish",
                    body.getString("callbackUrl"));
        }
    }

    @Test
    void transcode_shouldCallCorrectUrl() {
        HttpRequest mockRequest = mock(HttpRequest.class);
        HttpResponse mockResponse = mock(HttpResponse.class);
        doReturn(mockRequest).when(mockRequest).body(anyString());
        when(mockRequest.header(anyString(), anyString())).thenReturn(mockRequest);
        when(mockRequest.execute()).thenReturn(mockResponse);
        when(mockResponse.header("X-Fc-Request-Id")).thenReturn("fc-req-002");
        doReturn("async-ok").when(mockResponse).body();

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            httpUtilMock.when(() -> HttpUtil.createPost(anyString())).thenReturn(mockRequest);

            cloudFunctionTranscodeService.transcode(
                    "in.mp4", "out/", "v1", "t1", "j1",
                    "480p", 854, 480, "h264", "aac", "medium",
                    "https://cb.example.com/done"
            );

            httpUtilMock.verify(() -> HttpUtil.createPost(
                    "https://transcoe-master-video-transcode-pqrshwejna.cn-beijing.fcapp.run"
            ));
        }
    }
}
