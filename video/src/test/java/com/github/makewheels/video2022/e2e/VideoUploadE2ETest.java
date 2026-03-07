package com.github.makewheels.video2022.e2e;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class VideoUploadE2ETest extends BaseE2ETest {

    private static final String RAW_FILENAME = "e2e-test-video.mp4";

    /**
     * 创建视频并返回响应 data
     */
    private JSONObject createVideo() {
        JSONObject body = new JSONObject();
        body.put("videoType", "USER_UPLOAD");
        body.put("rawFilename", RAW_FILENAME);
        body.put("size", 1024000L);

        ResponseEntity<String> response = authPost(
                getBaseUrl() + "/video/create", body.toJSONString());
        assertEquals(200, response.getStatusCode().value());

        JSONObject result = JSONObject.parseObject(response.getBody());
        assertEquals(0, result.getIntValue("code"));

        JSONObject data = result.getJSONObject("data");
        assertNotNull(data);

        String videoId = data.getString("videoId");
        String fileId = data.getString("fileId");
        assertNotNull(videoId);
        assertNotNull(fileId);
        assertTrue(videoId.startsWith("v_"));
        assertTrue(fileId.startsWith("f_"));

        createdVideoIds.add(videoId);
        createdFileIds.add(fileId);

        return data;
    }

    /**
     * 获取上传凭证
     */
    private JSONObject getUploadCredentials(String fileId) {
        ResponseEntity<String> response = authGet(
                getBaseUrl() + "/file/getUploadCredentials?fileId=" + fileId);
        assertEquals(200, response.getStatusCode().value());

        JSONObject result = JSONObject.parseObject(response.getBody());
        assertEquals(0, result.getIntValue("code"));

        JSONObject data = result.getJSONObject("data");
        assertNotNull(data);
        assertNotNull(data.getString("bucket"));
        assertNotNull(data.getString("key"));
        assertNotNull(data.getString("endpoint"));
        assertNotNull(data.getString("accessKeyId"));
        assertNotNull(data.getString("secretKey"));
        assertNotNull(data.getString("sessionToken"));

        return data;
    }

    /**
     * 使用 STS 凭证上传文件到 OSS
     */
    private void uploadToOss(JSONObject credentials) {
        String endpoint = credentials.getString("endpoint");
        String accessKeyId = credentials.getString("accessKeyId");
        String secretKey = credentials.getString("secretKey");
        String sessionToken = credentials.getString("sessionToken");
        String bucket = credentials.getString("bucket");
        String key = credentials.getString("key");

        createdOssKeys.add(key);

        byte[] content = "e2e-test-fake-video-content".getBytes(StandardCharsets.UTF_8);
        OSS stsClient = new OSSClientBuilder().build(
                "https://" + endpoint, accessKeyId, secretKey, sessionToken);
        try {
            stsClient.putObject(bucket, key, new ByteArrayInputStream(content));
            log.info("OSS 上传完成: bucket={}, key={}", bucket, key);
        } finally {
            stsClient.shutdown();
        }
    }

    /**
     * 通知服务端上传完成
     */
    private void notifyUploadFinish(String fileId) {
        ResponseEntity<String> response = authGet(
                getBaseUrl() + "/file/uploadFinish?fileId=" + fileId);
        assertEquals(200, response.getStatusCode().value());

        JSONObject result = JSONObject.parseObject(response.getBody());
        assertEquals(0, result.getIntValue("code"));
    }

    @Test
    void testCreateAndUploadVideo() {
        // 1. 创建视频
        JSONObject createData = createVideo();
        String videoId = createData.getString("videoId");
        String fileId = createData.getString("fileId");
        assertNotNull(createData.getString("watchUrl"));

        // 2. 获取上传凭证
        JSONObject credentials = getUploadCredentials(fileId);
        String ossKey = credentials.getString("key");

        // 3. 上传文件到 OSS
        uploadToOss(credentials);

        // 4. 通知上传完成
        notifyUploadFinish(fileId);

        // 5. 验证 MongoDB 中的 Video
        Video video = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(video, "Video should exist in MongoDB");
        assertEquals(testUserId, video.getUploaderId());
        assertEquals("USER_UPLOAD", video.getVideoType());
        assertNotNull(video.getWatch());
        assertNotNull(video.getWatch().getWatchId());
        assertEquals(fileId, video.getRawFileId());

        // 6. 验证 MongoDB 中的 File
        File file = mongoTemplate.findById(fileId, File.class);
        assertNotNull(file, "File should exist in MongoDB");
        assertEquals(videoId, file.getVideoId());
        assertEquals("READY", file.getFileStatus());
        assertEquals(ossKey, file.getKey());
        assertNotNull(file.getSize());
        assertTrue(file.getSize() > 0);
        assertNotNull(file.getEtag());
        assertNotNull(file.getUploadTime());

        // 7. 验证 OSS 文件存在
        assertTrue(ossVideoService.doesObjectExist(ossKey),
                "Uploaded file should exist on OSS");

        log.info("testCreateAndUploadVideo 通过: videoId={}, fileId={}", videoId, fileId);
    }

    @Test
    void testCreateVideoAppearsInMyVideoList() {
        // 1. 创建视频
        JSONObject createData = createVideo();
        String videoId = createData.getString("videoId");

        // 2. 查询我的视频列表
        ResponseEntity<String> response = authGet(
                getBaseUrl() + "/video/getMyVideoList?skip=0&limit=10");
        assertEquals(200, response.getStatusCode().value());

        JSONObject result = JSONObject.parseObject(response.getBody());
        assertEquals(0, result.getIntValue("code"));

        JSONArray list = result.getJSONArray("data");
        assertNotNull(list);

        // 3. 验证刚创建的视频在列表中
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            JSONObject item = list.getJSONObject(i);
            if (videoId.equals(item.getString("id"))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Newly created video should appear in my video list");

        log.info("testCreateVideoAppearsInMyVideoList 通过: videoId={}", videoId);
    }
}
