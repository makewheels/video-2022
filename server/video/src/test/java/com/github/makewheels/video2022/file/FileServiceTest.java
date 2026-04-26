package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.file.constants.ObjectStorageProvider;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.system.context.Context;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class FileServiceTest extends BaseIntegrationTest {

    @Autowired
    private FileService fileService;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private FileAccessSignatureService fileAccessSignatureService;

    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        testUser = new User();
        testUser.setPhone("13800000001");
        testUser.setRegisterChannel("TEST");
        testUser.setToken("test-token-file");
        mongoTemplate.save(testUser);
        UserHolder.set(testUser);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        RequestContextHolder.resetRequestAttributes();
        cleanDatabase();
    }

    private Context buildContext(String videoId, String clientId, String sessionId) {
        Context context = new Context();
        context.setVideoId(videoId);
        context.setClientId(clientId);
        context.setSessionId(sessionId);
        return context;
    }

    private MockHttpServletResponse bindServletRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.101");
        request.addHeader("User-Agent", "FileServiceTest/1.0");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        return response;
    }

    // ──────────────────── createVideoFile ────────────────────

    @Test
    void createVideoFile_userUpload_savesWithCorrectFields() {
        Video video = new Video();
        video.setProvider(ObjectStorageProvider.ALIYUN_OSS);
        mongoTemplate.save(video);

        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename("holiday.mp4");
        dto.setUser(testUser);
        dto.setVideo(video);

        File file = fileService.createVideoFile(dto);

        assertNotNull(file.getId());
        assertTrue(file.getId().startsWith("f_"));
        assertEquals(FileType.RAW_VIDEO, file.getFileType());
        assertEquals(testUser.getId(), file.getUploaderId());
        assertEquals(ObjectStorageProvider.ALIYUN_OSS, file.getProvider());
        assertEquals(VideoType.USER_UPLOAD, file.getVideoType());
        assertEquals("holiday.mp4", file.getRawFilename());
        assertEquals("mp4", file.getExtension());
        assertEquals(FileStatus.CREATED, file.getFileStatus());
        assertFalse(file.getDeleted());

        // Verify persisted in MongoDB
        File fromDb = mongoTemplate.findById(file.getId(), File.class);
        assertNotNull(fromDb);
        assertEquals(file.getId(), fromDb.getId());
    }

    @Test
    void createVideoFile_userUpload_extensionIsLowercase() {
        Video video = new Video();
        video.setProvider(ObjectStorageProvider.ALIYUN_OSS);
        mongoTemplate.save(video);

        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename("Tutorial.MKV");
        dto.setUser(testUser);
        dto.setVideo(video);

        File file = fileService.createVideoFile(dto);

        assertEquals("mkv", file.getExtension());
        assertEquals("Tutorial.MKV", file.getRawFilename());
    }

    @Test
    void createVideoFile_youtube_defaultsToWebm() {
        Video video = new Video();
        video.setProvider(ObjectStorageProvider.ALIYUN_OSS);
        mongoTemplate.save(video);

        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.YOUTUBE);
        dto.setUser(testUser);
        dto.setVideo(video);

        File file = fileService.createVideoFile(dto);

        assertEquals("webm", file.getExtension());
        assertEquals(VideoType.YOUTUBE, file.getVideoType());
        assertNull(file.getRawFilename());
    }

    // ──────────────────── getKeyByFileId ────────────────────

    @Test
    void getKeyByFileId_existingFile_returnsKey() {
        File file = new File();
        file.setId("f_test_key_001");
        file.setKey("videos/abc/def/raw/test.mp4");
        mongoTemplate.save(file);

        String key = fileService.getKeyByFileId("f_test_key_001");
        assertEquals("videos/abc/def/raw/test.mp4", key);
    }

    @Test
    void getKeyByFileId_nonExistentFile_returnsNull() {
        String key = fileService.getKeyByFileId("f_non_existent_id");
        assertNull(key);
    }

    // ──────────────────── getUploadCredentials ────────────────────

    @Test
    void getUploadCredentials_returnsCredentialsWithProvider() {
        File file = new File();
        file.setId("f_cred_001");
        file.setKey("videos/abc/raw/video.mp4");
        file.setProvider(ObjectStorageProvider.ALIYUN_OSS);
        mongoTemplate.save(file);

        JSONObject mockCredentials = new JSONObject();
        mockCredentials.put("accessKeyId", "test-ak-id");
        mockCredentials.put("accessKeySecret", "test-ak-secret");
        mockCredentials.put("securityToken", "test-token");
        when(ossVideoService.generateUploadCredentials("videos/abc/raw/video.mp4"))
                .thenReturn(mockCredentials);

        JSONObject result = fileService.getUploadCredentials("f_cred_001");

        assertNotNull(result);
        assertEquals("test-ak-id", result.getString("accessKeyId"));
        assertEquals(ObjectStorageProvider.ALIYUN_OSS, result.getString("provider"));
    }

    @Test
    void getUploadCredentials_nullCredentials_throwsException() {
        File file = new File();
        file.setId("f_cred_fail");
        file.setKey("videos/fail/raw/video.mp4");
        mongoTemplate.save(file);

        when(ossVideoService.generateUploadCredentials(anyString())).thenReturn(null);

        assertThrows(Exception.class, () -> fileService.getUploadCredentials("f_cred_fail"));
    }

    // ──────────────────── uploadFinish ────────────────────

    @Test
    void uploadFinish_updatesFileStatusToReady() {
        File file = new File();
        file.setId("f_upload_001");
        file.setKey("videos/abc/raw/video.mp4");
        file.setFileStatus(FileStatus.CREATED);
        mongoTemplate.save(file);

        OSSObject ossObject = new OSSObject();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(2048L);
        metadata.setHeader("ETag", "abc123etag");
        metadata.setLastModified(new Date());
        ossObject.setObjectMetadata(metadata);
        when(ossVideoService.getObject("videos/abc/raw/video.mp4")).thenReturn(ossObject);

        fileService.uploadFinish("f_upload_001");

        File updated = mongoTemplate.findById("f_upload_001", File.class);
        assertNotNull(updated);
        assertEquals(FileStatus.READY, updated.getFileStatus());
        assertEquals(2048L, updated.getSize());
        assertNotNull(updated.getEtag());
        assertNotNull(updated.getUploadTime());
    }

    // ──────────────────── deleteFile ────────────────────

    @Test
    void deleteFile_marksDeletedAndSetsTimestamp() {
        File file = new File();
        file.setId("f_del_001");
        file.setKey("videos/abc/raw/delete-me.mp4");
        file.setDeleted(false);
        mongoTemplate.save(file);

        fileService.deleteFile(file);

        File deleted = mongoTemplate.findById("f_del_001", File.class);
        assertNotNull(deleted);
        assertTrue(deleted.getDeleted());
        assertNotNull(deleted.getDeleteTime());
    }

    // ──────────────────── generatePresignedUrl ────────────────────

    @Test
    void generatePresignedUrl_delegatesToOss() {
        when(ossVideoService.generatePresignedUrl(anyString(), any()))
                .thenReturn("https://oss.example.com/signed-url");

        String url = fileService.generatePresignedUrl("videos/test/key.ts",
                Duration.ofHours(1));

        assertEquals("https://oss.example.com/signed-url", url);
    }

    @Test
    void access_validSignature_redirectsToSignedUrl() {
        TsFile tsFile = new TsFile();
        tsFile.setId("f_ts_access_001");
        tsFile.setKey("videos/access/segment0.ts");
        mongoTemplate.save(tsFile);

        MockHttpServletResponse response = bindServletRequestContext();
        when(ossVideoService.generatePresignedUrl("videos/access/segment0.ts", Duration.ofHours(3)))
                .thenReturn("https://oss.example.com/access/segment0.ts?sig=ok");

        Context context = buildContext("v_access_001", "client_access_001", "session_access_001");
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-001";
        String sign = fileAccessSignatureService.generateSignature(
                context.getVideoId(),
                context.getClientId(),
                context.getSessionId(),
                "720p",
                tsFile.getId(),
                timestamp,
                nonce
        );

        fileService.access(context, "720p", tsFile.getId(), timestamp, nonce, sign);

        assertEquals(302, response.getStatus());
        assertEquals("https://oss.example.com/access/segment0.ts?sig=ok", response.getRedirectedUrl());
    }

    @Test
    void access_invalidSignature_throwsVideoException() {
        Context context = buildContext("v_access_002", "client_access_002", "session_access_002");
        String timestamp = String.valueOf(System.currentTimeMillis());

        VideoException exception = assertThrows(VideoException.class, () ->
                fileService.access(context, "720p", "f_ts_access_invalid", timestamp, "nonce-002", "bad-sign")
        );

        assertEquals(ErrorCode.FILE_ACCESS_SIGNATURE_INVALID, exception.getErrorCode());
    }

    // ──────────────────── changeObjectAcl ────────────────────

    @Test
    void changeObjectAcl_updatesAclOnFile() {
        File file = new File();
        file.setId("f_acl_001");
        file.setKey("videos/acl/test.mp4");
        mongoTemplate.save(file);

        fileService.changeObjectAcl("f_acl_001", "public-read");

        File updated = mongoTemplate.findById("f_acl_001", File.class);
        assertNotNull(updated);
        assertEquals("public-read", updated.getAcl());
    }

    // ──────────────────── changeStorageClass ────────────────────

    @Test
    void changeStorageClass_updatesStorageClassOnFile() {
        File file = new File();
        file.setId("f_sc_001");
        file.setKey("videos/sc/test.mp4");
        file.setStorageClass("Standard");
        mongoTemplate.save(file);

        fileService.changeStorageClass("f_sc_001", "IA");

        File updated = mongoTemplate.findById("f_sc_001", File.class);
        assertNotNull(updated);
        assertEquals("IA", updated.getStorageClass());
    }
}
