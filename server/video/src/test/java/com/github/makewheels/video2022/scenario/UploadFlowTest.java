package com.github.makewheels.video2022.scenario;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.service.VideoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * End-to-end scenario test for the video upload flow.
 * <p>
 * Exercises the full service-level path: create video → get upload credentials →
 * raw file upload finish, verifying MongoDB state at each step.
 */
class UploadFlowTest extends BaseIntegrationTest {

    @Autowired
    private VideoService videoService;

    @Autowired
    private FileService fileService;

    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        testUser = new User();
        testUser.setId("u_test_upload_user");
        testUser.setPhone("13800138000");
        testUser.setToken("test-upload-flow-token");
        mongoTemplate.save(testUser);

        UserHolder.set(testUser);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void testFullUploadFlow() throws Exception {
        // ── Step 1: Create video ────────────────────────────────────────────
        CreateVideoDTO createVideoDTO = new CreateVideoDTO();
        createVideoDTO.setVideoType("USER_UPLOAD");
        createVideoDTO.setRawFilename("test-video.mp4");

        JSONObject createResult = videoService.create(createVideoDTO);

        assertNotNull(createResult, "Create result should not be null");
        String videoId = createResult.getString("videoId");
        String fileId = createResult.getString("fileId");
        assertNotNull(videoId, "videoId should be in response");
        assertNotNull(fileId, "fileId should be in response");
        assertTrue(videoId.startsWith("v_"), "videoId should start with v_");
        assertTrue(fileId.startsWith("f_"), "fileId should start with f_");

        // Verify MongoDB state after creation
        Video video = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(video, "Video should be persisted in MongoDB");
        assertNotNull(video.getWatch(), "Watch should be initialised");
        assertNotNull(video.getWatch().getWatchId());

        File rawFile = mongoTemplate.findById(fileId, File.class);
        assertNotNull(rawFile, "Raw file should be persisted in MongoDB");
        assertEquals("mp4", rawFile.getExtension());

        // ── Step 2: Get upload credentials ──────────────────────────────────
        JSONObject mockCredentials = new JSONObject();
        mockCredentials.put("accessKeyId", "test-access-key-id");
        mockCredentials.put("accessKeySecret", "test-access-key-secret");
        mockCredentials.put("securityToken", "test-security-token");
        mockCredentials.put("bucket", "test-bucket");
        mockCredentials.put("key", rawFile.getKey());

        when(ossVideoService.generateUploadCredentials(anyString()))
                .thenReturn(mockCredentials);

        JSONObject credentials = fileService.getUploadCredentials(fileId);
        assertNotNull(credentials, "Upload credentials should be returned");

        // ── Step 3: Simulate upload completion ──────────────────────────────
        // In the real flow the client uploads directly to OSS then calls uploadFinish.
        // Here we update MongoDB directly to simulate a successful upload.
        rawFile.setFileStatus("READY");
        rawFile.setSize(10_485_760L); // 10 MB
        rawFile.setEtag("test-etag-12345");
        mongoTemplate.save(rawFile);

        // ── Step 4: Raw file upload finish ──────────────────────────────────
        // rawFileUploadFinish spawns an async thread for transcoding
        videoService.rawFileUploadFinish(videoId);

        Thread.sleep(1_000);

        Video updatedVideo = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(updatedVideo, "Video should still exist after upload finish");
    }
}
