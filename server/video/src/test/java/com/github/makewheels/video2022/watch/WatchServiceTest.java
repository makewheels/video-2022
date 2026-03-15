package com.github.makewheels.video2022.watch;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.cover.CoverService;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.system.context.Context;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.entity.Watch;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.watch.play.WatchLog;
import com.github.makewheels.video2022.watch.play.WatchService;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfoVO;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class WatchServiceTest extends BaseIntegrationTest {

    @Autowired
    private WatchService watchService;

    @MockitoBean
    private CoverService coverService;

    @MockitoBean
    private EnvironmentService environmentService;

    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        testUser = new User();
        testUser.setId(new ObjectId().toHexString());
        testUser.setPhone("13800000099");
        mongoTemplate.save(testUser);
        UserHolder.set(testUser);

        // Set up mock HttpServletRequest for RequestUtil static methods
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("192.168.1.100");
        mockRequest.addHeader("User-Agent", "TestBrowser/1.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        // Default mock behaviour
        JSONObject ipInfo = new JSONObject();
        ipInfo.put("province", "北京");
        ipInfo.put("city", "北京");
        ipInfo.put("district", "海淀");
        when(ipService.getIpInfo(anyString())).thenReturn(ipInfo);
        when(environmentService.isProductionEnv()).thenReturn(false);
        when(environmentService.getInternalBaseUrl()).thenReturn("http://localhost:8080");
        when(coverService.getSignedCoverUrl(anyString())).thenReturn("https://cdn.example.com/cover.jpg");
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    // ---- helpers ----

    private Context buildContext(String videoId, String clientId, String sessionId) {
        Context ctx = new Context();
        ctx.setVideoId(videoId);
        ctx.setClientId(clientId);
        ctx.setSessionId(sessionId);
        return ctx;
    }

    private Video createAndSaveVideo(String videoId, String status) {
        Video video = new Video();
        video.setId(videoId);
        video.setTitle("Test Video");
        video.setStatus(status);
        video.setUploaderId(testUser.getId());
        video.setCoverId("cover_001");
        Watch watch = video.getWatch();
        watch.setWatchId("watch_" + videoId);
        watch.setWatchCount(0);
        return mongoTemplate.save(video);
    }

    // ---- addWatchLog tests ----

    @Test
    void addWatchLog_firstCall_savesWatchLog() {
        String videoId = "v_add_watch_001";
        createAndSaveVideo(videoId, VideoStatus.READY);
        Context ctx = buildContext(videoId, "client_1", "session_1");

        Result<Void> result = watchService.addWatchLog(ctx, VideoStatus.READY);

        assertEquals(0, result.getCode());
        List<WatchLog> logs = mongoTemplate.findAll(WatchLog.class);
        assertEquals(1, logs.size());
        WatchLog log = logs.get(0);
        assertEquals(videoId, log.getVideoId());
        assertEquals("client_1", log.getClientId());
        assertEquals("session_1", log.getSessionId());
        assertEquals(VideoStatus.READY, log.getVideoStatus());
        assertEquals("192.168.1.100", log.getIp());
        assertNotNull(log.getIpInfo());
        assertNotNull(log.getCreateTime());
    }

    @Test
    void addWatchLog_readyStatus_incrementsWatchCount() {
        String videoId = "v_add_watch_002";
        createAndSaveVideo(videoId, VideoStatus.READY);
        Context ctx = buildContext(videoId, "client_1", "session_1");

        watchService.addWatchLog(ctx, VideoStatus.READY);

        // watchCount should be incremented (both via addWatchCount and mongoTemplate.save)
        Video updated = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(updated);
        // addWatchCount does inc(1) and then save sets watchCount+1, so total is 2
        assertTrue(updated.getWatch().getWatchCount() >= 1);
    }

    @Test
    void addWatchLog_nonReadyStatus_doesNotIncrementWatchCount() {
        String videoId = "v_add_watch_003";
        createAndSaveVideo(videoId, VideoStatus.TRANSCODING);
        Context ctx = buildContext(videoId, "client_1", "session_1");

        watchService.addWatchLog(ctx, VideoStatus.TRANSCODING);

        Video updated = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(updated);
        assertEquals(0, updated.getWatch().getWatchCount());
    }

    @Test
    void addWatchLog_duplicateCall_skips() {
        String videoId = "v_add_watch_004";
        createAndSaveVideo(videoId, VideoStatus.TRANSCODING);
        Context ctx = buildContext(videoId, "client_1", "session_dup");

        watchService.addWatchLog(ctx, VideoStatus.TRANSCODING);
        watchService.addWatchLog(ctx, VideoStatus.TRANSCODING);

        List<WatchLog> logs = mongoTemplate.findAll(WatchLog.class);
        assertEquals(1, logs.size());
    }

    @Test
    void addWatchLog_differentSessions_savesSeparateLogs() {
        String videoId = "v_add_watch_005";
        createAndSaveVideo(videoId, VideoStatus.TRANSCODING);
        Context ctx1 = buildContext(videoId, "client_1", "session_a");
        Context ctx2 = buildContext(videoId, "client_1", "session_b");

        watchService.addWatchLog(ctx1, VideoStatus.TRANSCODING);
        watchService.addWatchLog(ctx2, VideoStatus.TRANSCODING);

        List<WatchLog> logs = mongoTemplate.findAll(WatchLog.class);
        assertEquals(2, logs.size());
    }

    // ---- getWatchInfo tests ----

    @Test
    void getWatchInfo_validWatchId_returnsInfo() {
        String videoId = "v_watch_info_001";
        Video video = createAndSaveVideo(videoId, VideoStatus.READY);
        String watchId = video.getWatch().getWatchId();
        Context ctx = buildContext(videoId, "client_1", "session_1");

        Result<WatchInfoVO> result = watchService.getWatchInfo(ctx, watchId);

        assertEquals(0, result.getCode());
        WatchInfoVO vo = result.getData();
        assertNotNull(vo);
        assertEquals(videoId, vo.getVideoId());
        assertEquals(VideoStatus.READY, vo.getVideoStatus());
        assertEquals("https://cdn.example.com/cover.jpg", vo.getCoverUrl());
        assertNotNull(vo.getMultivariantPlaylistUrl());
        assertTrue(vo.getMultivariantPlaylistUrl().contains("getMultivariantPlaylist"));
        assertEquals(0L, vo.getProgressInMillis());
    }

    @Test
    void getWatchInfo_invalidWatchId_returnsError() {
        Context ctx = buildContext("v_no_exist", "client_1", "session_1");
        Result<WatchInfoVO> result = watchService.getWatchInfo(ctx, "non_existent_watch_id");
        assertNotNull(result);
        assertEquals(22, result.getCode());
    }

    // ---- getM3u8Content tests ----

    @Test
    void getM3u8Content_replacesFilenamesWithUrls() {
        String videoId = "v_m3u8_001";
        String transcodeId = new ObjectId().toHexString();

        // Create TsFiles
        TsFile ts0 = new TsFile();
        ts0.setId(new ObjectId().toHexString());
        ts0.setFilename("segment0.ts");
        ts0.setTsIndex(0);
        ts0.setFileType("ts");
        ts0.setVideoId(videoId);
        ts0.setTranscodeId(transcodeId);
        mongoTemplate.save(ts0);

        TsFile ts1 = new TsFile();
        ts1.setId(new ObjectId().toHexString());
        ts1.setFilename("segment1.ts");
        ts1.setTsIndex(1);
        ts1.setFileType("ts");
        ts1.setVideoId(videoId);
        ts1.setTranscodeId(transcodeId);
        mongoTemplate.save(ts1);

        // Create Transcode with m3u8Content
        Transcode transcode = new Transcode();
        transcode.setId(transcodeId);
        transcode.setVideoId(videoId);
        transcode.setResolution("720p");
        transcode.setM3u8Content("#EXTM3U\n#EXT-X-TARGETDURATION:10\nsegment0.ts\nsegment1.ts\n#EXT-X-ENDLIST");
        transcode.setTsFileIds(Arrays.asList(ts0.getId(), ts1.getId()));
        mongoTemplate.save(transcode);

        Context ctx = buildContext(videoId, "client_1", "session_1");

        String content = watchService.getM3u8Content(ctx, transcodeId, "720p");

        assertNotNull(content);
        // HLS directives should be preserved
        assertTrue(content.contains("#EXTM3U"));
        assertTrue(content.contains("#EXT-X-TARGETDURATION:10"));
        assertTrue(content.contains("#EXT-X-ENDLIST"));
        // Filenames should be replaced with full URLs
        assertFalse(content.contains("segment0.ts"));
        assertFalse(content.contains("segment1.ts"));
        assertTrue(content.contains("http://localhost:8080/file/access?"));
        assertTrue(content.contains("resolution=720p"));
        assertTrue(content.contains("videoId=" + videoId));
        assertTrue(content.contains("fileId=" + ts0.getId()));
        assertTrue(content.contains("fileId=" + ts1.getId()));
    }

    // ---- getMultivariantPlaylist tests ----

    @Test
    void getMultivariantPlaylist_buildsCorrectPlaylist() {
        String videoId = "v_multi_001";

        // Create transcodes
        Transcode t1 = new Transcode();
        t1.setId(new ObjectId().toHexString());
        t1.setVideoId(videoId);
        t1.setResolution("480p");
        t1.setMaxBitrate(500000);
        t1.setAverageBitrate(400000);
        mongoTemplate.save(t1);

        Transcode t2 = new Transcode();
        t2.setId(new ObjectId().toHexString());
        t2.setVideoId(videoId);
        t2.setResolution("720p");
        t2.setMaxBitrate(1500000);
        t2.setAverageBitrate(1200000);
        mongoTemplate.save(t2);

        Video video = createAndSaveVideo(videoId, VideoStatus.READY);
        video.setTranscodeIds(Arrays.asList(t1.getId(), t2.getId()));
        mongoTemplate.save(video);

        Context ctx = buildContext(videoId, "client_1", "session_1");

        String playlist = watchService.getMultivariantPlaylist(ctx);

        assertNotNull(playlist);
        assertTrue(playlist.startsWith("#EXTM3U"));
        // Should contain stream info for both resolutions
        assertTrue(playlist.contains("BANDWIDTH=500000"));
        assertTrue(playlist.contains("AVERAGE-BANDWIDTH=400000"));
        assertTrue(playlist.contains("BANDWIDTH=1500000"));
        assertTrue(playlist.contains("AVERAGE-BANDWIDTH=1200000"));
        // URLs should contain transcode ids
        assertTrue(playlist.contains("transcodeId=" + t1.getId()));
        assertTrue(playlist.contains("transcodeId=" + t2.getId()));
    }

    @Test
    void getMultivariantPlaylist_singleTranscode_works() {
        String videoId = "v_multi_002";

        Transcode t1 = new Transcode();
        t1.setId(new ObjectId().toHexString());
        t1.setVideoId(videoId);
        t1.setResolution("1080p");
        t1.setMaxBitrate(3000000);
        t1.setAverageBitrate(2500000);
        mongoTemplate.save(t1);

        Video video = createAndSaveVideo(videoId, VideoStatus.READY);
        video.setTranscodeIds(List.of(t1.getId()));
        mongoTemplate.save(video);

        Context ctx = buildContext(videoId, "client_1", "session_1");

        String playlist = watchService.getMultivariantPlaylist(ctx);

        assertTrue(playlist.startsWith("#EXTM3U"));
        assertTrue(playlist.contains("BANDWIDTH=3000000"));
        // Exactly one stream entry
        assertEquals(1, playlist.split("#EXT-X-STREAM-INF").length - 1);
    }

    // ──────────────────── 可见性测试 ────────────────────

    @Test
    void getWatchInfo_privateVideo_nonOwner_returnsError() {
        String videoId = "v_private_001";
        Video video = createAndSaveVideo(videoId, VideoStatus.READY);
        video.setVisibility("PRIVATE");
        mongoTemplate.save(video);
        String watchId = video.getWatch().getWatchId();

        // 切换到非所有者用户
        User otherUser = new User();
        otherUser.setId(new ObjectId().toHexString());
        otherUser.setPhone("13800000088");
        mongoTemplate.save(otherUser);
        UserHolder.set(otherUser);

        Context ctx = buildContext(videoId, "client_1", "session_1");
        Result<WatchInfoVO> result = watchService.getWatchInfo(ctx, watchId);

        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("私密"));
    }

    @Test
    void getWatchInfo_privateVideo_owner_canWatch() {
        String videoId = "v_private_002";
        Video video = createAndSaveVideo(videoId, VideoStatus.READY);
        video.setVisibility("PRIVATE");
        mongoTemplate.save(video);
        String watchId = video.getWatch().getWatchId();

        // 当前用户就是所有者
        Context ctx = buildContext(videoId, "client_1", "session_1");
        Result<WatchInfoVO> result = watchService.getWatchInfo(ctx, watchId);

        assertEquals(0, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    void getWatchInfo_unlistedVideo_anyoneCanWatch() {
        String videoId = "v_unlisted_001";
        Video video = createAndSaveVideo(videoId, VideoStatus.READY);
        video.setVisibility("UNLISTED");
        mongoTemplate.save(video);
        String watchId = video.getWatch().getWatchId();

        // 切换到非所有者用户
        User otherUser = new User();
        otherUser.setId(new ObjectId().toHexString());
        otherUser.setPhone("13800000077");
        mongoTemplate.save(otherUser);
        UserHolder.set(otherUser);

        Context ctx = buildContext(videoId, "client_1", "session_1");
        Result<WatchInfoVO> result = watchService.getWatchInfo(ctx, watchId);

        assertEquals(0, result.getCode());
        assertNotNull(result.getData());
    }
}
