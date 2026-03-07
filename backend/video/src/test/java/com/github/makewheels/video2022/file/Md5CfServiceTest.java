package com.github.makewheels.video2022.file;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.file.md5.FileMd5DTO;
import com.github.makewheels.video2022.file.md5.Md5CfService;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Md5CfServiceTest {

    @Mock
    private EnvironmentService environmentService;

    @InjectMocks
    private Md5CfService md5CfService;

    private static final String CF_URL = "https://cf.example.com/md5";

    private void setupCfUrl() {
        when(environmentService.getAliyunCfUrlGetOssObjectMd5()).thenReturn(CF_URL);
    }

    // --- Single file MD5 ---

    @Test
    void getOssObjectMd5_single_shouldSetMd5OnDto() {
        setupCfUrl();

        FileMd5DTO dto = new FileMd5DTO();
        dto.setFileId("file001");
        dto.setKey("videos/raw/file001.mp4");

        JSONObject response = new JSONObject();
        response.put("aliyunRequestId", "req-001");
        JSONObject obj = new JSONObject();
        obj.put("fileId", "file001");
        obj.put("key", "videos/raw/file001.mp4");
        obj.put("md5", "abc123def456");
        response.put("objectList", JSON.parseArray(JSON.toJSONString(
                java.util.Collections.singletonList(obj))));

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            httpUtilMock.when(() -> HttpUtil.post(eq(CF_URL), anyString()))
                    .thenReturn(response.toJSONString());

            md5CfService.getOssObjectMd5(dto);

            assertEquals("abc123def456", dto.getMd5());

            // Verify request body
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            httpUtilMock.verify(() -> HttpUtil.post(eq(CF_URL), bodyCaptor.capture()));

            JSONObject requestBody = JSONObject.parseObject(bodyCaptor.getValue());
            assertFalse(requestBody.getBooleanValue("callback"));
            assertEquals(1, requestBody.getJSONArray("objectList").size());
            assertEquals("file001",
                    requestBody.getJSONArray("objectList").getJSONObject(0).getString("fileId"));
        }
    }

    @Test
    void getOssObjectMd5_single_shouldSendKeyInObjectList() {
        setupCfUrl();

        FileMd5DTO dto = new FileMd5DTO();
        dto.setFileId("f1");
        dto.setKey("some/path/file.mp4");

        JSONObject response = new JSONObject();
        JSONObject obj = new JSONObject();
        obj.put("fileId", "f1");
        obj.put("key", "some/path/file.mp4");
        obj.put("md5", "md5result");
        response.put("objectList", JSON.parseArray(JSON.toJSONString(
                java.util.Collections.singletonList(obj))));

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            httpUtilMock.when(() -> HttpUtil.post(eq(CF_URL), anyString()))
                    .thenReturn(response.toJSONString());

            md5CfService.getOssObjectMd5(dto);

            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            httpUtilMock.verify(() -> HttpUtil.post(eq(CF_URL), bodyCaptor.capture()));

            JSONObject body = JSONObject.parseObject(bodyCaptor.getValue());
            assertEquals("some/path/file.mp4",
                    body.getJSONArray("objectList").getJSONObject(0).getString("key"));
        }
    }

    // --- Batch MD5 ---

    @Test
    void getOssObjectMd5_batch_shouldSetMd5OnAllDtos() {
        setupCfUrl();

        FileMd5DTO dto1 = new FileMd5DTO();
        dto1.setFileId("file001");
        dto1.setKey("videos/raw/file001.mp4");

        FileMd5DTO dto2 = new FileMd5DTO();
        dto2.setFileId("file002");
        dto2.setKey("videos/raw/file002.mp4");

        List<FileMd5DTO> dtoList = Arrays.asList(dto1, dto2);

        JSONObject response = new JSONObject();
        JSONObject obj1 = new JSONObject();
        obj1.put("fileId", "file001");
        obj1.put("key", "videos/raw/file001.mp4");
        obj1.put("md5", "md5_aaa");
        JSONObject obj2 = new JSONObject();
        obj2.put("fileId", "file002");
        obj2.put("key", "videos/raw/file002.mp4");
        obj2.put("md5", "md5_bbb");
        response.put("objectList", JSON.parseArray(JSON.toJSONString(
                Arrays.asList(obj1, obj2))));

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            httpUtilMock.when(() -> HttpUtil.post(eq(CF_URL), anyString()))
                    .thenReturn(response.toJSONString());

            md5CfService.getOssObjectMd5(dtoList);

            assertEquals("md5_aaa", dto1.getMd5());
            assertEquals("md5_bbb", dto2.getMd5());

            // Verify request body
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            httpUtilMock.verify(() -> HttpUtil.post(eq(CF_URL), bodyCaptor.capture()));

            JSONObject requestBody = JSONObject.parseObject(bodyCaptor.getValue());
            assertFalse(requestBody.getBooleanValue("callback"));
            assertEquals(2, requestBody.getJSONArray("objectList").size());
        }
    }

    @Test
    void getOssObjectMd5_batch_shouldMatchMd5ByKey() {
        setupCfUrl();

        FileMd5DTO dto1 = new FileMd5DTO();
        dto1.setFileId("f1");
        dto1.setKey("key-a");

        FileMd5DTO dto2 = new FileMd5DTO();
        dto2.setFileId("f2");
        dto2.setKey("key-b");

        // Response returns objects in reversed order
        JSONObject response = new JSONObject();
        JSONObject objB = new JSONObject();
        objB.put("key", "key-b");
        objB.put("md5", "md5-for-b");
        JSONObject objA = new JSONObject();
        objA.put("key", "key-a");
        objA.put("md5", "md5-for-a");
        response.put("objectList", JSON.parseArray(JSON.toJSONString(
                Arrays.asList(objB, objA))));

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            httpUtilMock.when(() -> HttpUtil.post(eq(CF_URL), anyString()))
                    .thenReturn(response.toJSONString());

            md5CfService.getOssObjectMd5(Arrays.asList(dto1, dto2));

            // MD5 should be matched by key, not by order
            assertEquals("md5-for-a", dto1.getMd5());
            assertEquals("md5-for-b", dto2.getMd5());
        }
    }
}
