package com.github.makewheels.video2022.file;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.access.FileAccessLog;
import com.github.makewheels.video2022.file.access.FileAccessLogService;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.file.constants.ObjectStorageProvider;
import com.github.makewheels.video2022.finance.fee.ossaccess.OssAccessFee;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileAccessLogServiceTest extends BaseIntegrationTest {

    @Autowired
    private FileAccessLogService fileAccessLogService;

    private User testUser;
    private Video testVideo;
    private Transcode testTranscode;
    private TsFile testTsFile;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        testUser = new User();
        testUser.setPhone("13800000002");
        testUser.setRegisterChannel("TEST");
        mongoTemplate.save(testUser);

        testVideo = new Video();
        testVideo.setUploaderId(testUser.getId());
        testVideo.setOwnerId(testUser.getId());
        mongoTemplate.save(testVideo);

        testTranscode = new Transcode();
        testTranscode.setId("tr_test_001");
        testTranscode.setVideoId(testVideo.getId());
        testTranscode.setResolution("720p");
        mongoTemplate.save(testTranscode);

        testTsFile = new TsFile();
        testTsFile.setId("f_ts_test_001");
        testTsFile.setVideoId(testVideo.getId());
        testTsFile.setTranscodeId(testTranscode.getId());
        testTsFile.setKey("videos/abc/transcode/720p/seg-0.ts");
        testTsFile.setSize(102400L);
        testTsFile.setEtag("etag123");
        testTsFile.setFileType(FileType.TRANSCODE_TS);
        testTsFile.setProvider(ObjectStorageProvider.ALIYUN_OSS);
        testTsFile.setStorageClass("Standard");
        testTsFile.setFilename("seg-0.ts");
        testTsFile.setExtension("ts");
        mongoTemplate.save(testTsFile);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    // ──────────────────── saveAccessLog ────────────────────

    @Test
    void saveAccessLog_createsProperRecord() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        FileAccessLog log = fileAccessLogService.saveAccessLog(
                request, testVideo.getId(), "client_001", "session_001",
                "720p", testTsFile.getId());

        assertNotNull(log);
        assertNotNull(log.getId());
        assertEquals(testVideo.getId(), log.getVideoId());
        assertEquals(testUser.getId(), log.getUserId());
        assertEquals(testTsFile.getId(), log.getFileId());
        assertEquals(FileType.TRANSCODE_TS, log.getFileType());
        assertEquals("client_001", log.getClientId());
        assertEquals("session_001", log.getSessionId());
        assertEquals(testTranscode.getId(), log.getTranscodeId());
        assertEquals("720p", log.getResolution());
        assertEquals("192.168.1.100", log.getIp());
        assertNotNull(log.getCreateTime());

        // Properties copied from TsFile
        assertEquals(testTsFile.getKey(), log.getKey());
        assertEquals(testTsFile.getSize(), log.getSize());
        assertEquals(testTsFile.getStorageClass(), log.getStorageClass());
    }

    @Test
    void saveAccessLog_persistsToMongoDB() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        FileAccessLog log = fileAccessLogService.saveAccessLog(
                request, testVideo.getId(), "client_002", "session_002",
                "1080p", testTsFile.getId());

        FileAccessLog fromDb = mongoTemplate.findById(log.getId(), FileAccessLog.class);
        assertNotNull(fromDb);
        assertEquals(log.getVideoId(), fromDb.getVideoId());
        assertEquals(log.getFileId(), fromDb.getFileId());
    }

    // ──────────────────── handleAccessLog (saveAccessLog + saveFee) ────────────────────

    @Test
    void handleAccessLog_createsLogAndFee() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.16.0.1");

        fileAccessLogService.handleAccessLog(
                request, testVideo.getId(), "client_003", "session_003",
                "720p", testTsFile.getId());

        // Verify access log was created
        List<FileAccessLog> logs = mongoTemplate.findAll(FileAccessLog.class);
        assertEquals(1, logs.size());
        assertEquals(testVideo.getId(), logs.get(0).getVideoId());

        // Verify fee was created
        List<OssAccessFee> fees = mongoTemplate.findAll(OssAccessFee.class);
        assertEquals(1, fees.size());
        OssAccessFee fee = fees.get(0);
        assertEquals(testUser.getId(), fee.getUserId());
        assertEquals(testVideo.getId(), fee.getVideoId());
        assertEquals(testTsFile.getId(), fee.getFileId());
        assertNotNull(fee.getFeePrice());
    }

    // ──────────────────── saveFee ────────────────────

    @Test
    void saveFee_createsOssAccessFee() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        FileAccessLog accessLog = new FileAccessLog();
        accessLog.setUserId(testUser.getId());
        accessLog.setKey("videos/abc/transcode/720p/seg-0.ts");
        accessLog.setSize(102400L);
        accessLog.setStorageClass("Standard");
        accessLog.setCreateTime(new java.util.Date());
        mongoTemplate.save(accessLog);

        fileAccessLogService.saveFee(accessLog, request, testVideo.getId(),
                "client_004", "session_004", "720p", testTsFile.getId());

        List<OssAccessFee> fees = mongoTemplate.findAll(OssAccessFee.class);
        assertEquals(1, fees.size());
        OssAccessFee fee = fees.get(0);
        assertEquals(testUser.getId(), fee.getUserId());
        assertNotNull(fee.getUnitPrice());
        assertNotNull(fee.getFeePrice());
        assertTrue(fee.getFeePrice().compareTo(java.math.BigDecimal.ZERO) > 0);
    }

    @Test
    void saveAccessLog_multipleAccesses_createsSeparateRecords() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1.2.3.4");

        FileAccessLog log1 = fileAccessLogService.saveAccessLog(
                request, testVideo.getId(), "client_a", "session_a",
                "720p", testTsFile.getId());
        FileAccessLog log2 = fileAccessLogService.saveAccessLog(
                request, testVideo.getId(), "client_b", "session_b",
                "720p", testTsFile.getId());

        assertNotEquals(log1.getId(), log2.getId());
        List<FileAccessLog> all = mongoTemplate.findAll(FileAccessLog.class);
        assertEquals(2, all.size());
    }
}
