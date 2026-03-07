package com.github.makewheels.video2022.watch;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.watch.heartbeat.Heartbeat;
import com.github.makewheels.video2022.watch.heartbeat.HeartbeatService;
import com.github.makewheels.video2022.watch.heartbeat.PlayerStatus;
import com.github.makewheels.video2022.watch.progress.Progress;
import com.github.makewheels.video2022.watch.progress.ProgressService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatProgressTest extends BaseIntegrationTest {

    @Autowired
    private HeartbeatService heartbeatService;

    @Autowired
    private ProgressService progressService;

    private static final String VIDEO_ID = "v_test_heartbeat_001";
    private static final String CLIENT_ID = "client_test_001";
    private static final String SESSION_ID = "session_test_001";

    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        testUser = new User();
        testUser.setId(new ObjectId().toHexString());
        testUser.setPhone("13800000001");
        mongoTemplate.save(testUser);

        UserHolder.set(testUser);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    private Heartbeat buildHeartbeat(String videoId, String clientId, String sessionId,
                                     Long playerTime, String playerStatus) {
        Heartbeat heartbeat = new Heartbeat();
        heartbeat.setVideoId(videoId);
        heartbeat.setClientId(clientId);
        heartbeat.setSessionId(sessionId);
        heartbeat.setPlayerTime(playerTime);
        heartbeat.setPlayerStatus(playerStatus);
        heartbeat.setClientTime(new Date());
        return heartbeat;
    }

    // --- Heartbeat saved & progress created when playerStatus=PLAYING ---

    @Test
    void heartbeat_playing_savesHeartbeatAndCreatesProgress() {
        Heartbeat heartbeat = buildHeartbeat(VIDEO_ID, CLIENT_ID, SESSION_ID, 5000L, PlayerStatus.PLAYING);

        heartbeatService.add(heartbeat);

        // Verify heartbeat is persisted
        List<Heartbeat> heartbeats = mongoTemplate.findAll(Heartbeat.class);
        assertEquals(1, heartbeats.size());
        Heartbeat saved = heartbeats.get(0);
        assertNotNull(saved.getId());
        assertEquals(VIDEO_ID, saved.getVideoId());
        assertEquals(CLIENT_ID, saved.getClientId());
        assertEquals(testUser.getId(), saved.getViewerId());
        assertEquals(PlayerStatus.PLAYING, saved.getPlayerStatus());
        assertEquals(5000L, saved.getPlayerTime());
        assertNotNull(saved.getCreateTime());

        // Verify progress is created
        Progress progress = progressService.getProgress(VIDEO_ID, testUser.getId(), CLIENT_ID);
        assertNotNull(progress);
        assertEquals(VIDEO_ID, progress.getVideoId());
        assertEquals(testUser.getId(), progress.getViewerId());
        assertEquals(CLIENT_ID, progress.getClientId());
        assertEquals(5000L, progress.getProgressInMillis());
    }

    // --- playerStatus=PAUSED does NOT update progress ---

    @Test
    void heartbeat_paused_doesNotCreateProgress() {
        Heartbeat heartbeat = buildHeartbeat(VIDEO_ID, CLIENT_ID, SESSION_ID, 3000L, "paused");

        heartbeatService.add(heartbeat);

        // Heartbeat should still be saved
        List<Heartbeat> heartbeats = mongoTemplate.findAll(Heartbeat.class);
        assertEquals(1, heartbeats.size());

        // Progress should NOT exist
        Progress progress = progressService.getProgress(VIDEO_ID, testUser.getId(), CLIENT_ID);
        assertNull(progress);
    }

    @Test
    void heartbeat_paused_doesNotOverwriteExistingProgress() {
        // First: create progress via a PLAYING heartbeat
        heartbeatService.add(buildHeartbeat(VIDEO_ID, CLIENT_ID, SESSION_ID, 8000L, PlayerStatus.PLAYING));

        // Second: send a PAUSED heartbeat at a different playerTime
        heartbeatService.add(buildHeartbeat(VIDEO_ID, CLIENT_ID, SESSION_ID, 12000L, "paused"));

        // Progress should still reflect the PLAYING heartbeat's time
        Progress progress = progressService.getProgress(VIDEO_ID, testUser.getId(), CLIENT_ID);
        assertNotNull(progress);
        assertEquals(8000L, progress.getProgressInMillis());
    }

    // --- Multiple heartbeats: progress tracks latest playerTime ---

    @Test
    void multipleHeartbeats_progressTracksLatestPlayerTime() {
        heartbeatService.add(buildHeartbeat(VIDEO_ID, CLIENT_ID, SESSION_ID, 1000L, PlayerStatus.PLAYING));
        heartbeatService.add(buildHeartbeat(VIDEO_ID, CLIENT_ID, SESSION_ID, 5000L, PlayerStatus.PLAYING));
        heartbeatService.add(buildHeartbeat(VIDEO_ID, CLIENT_ID, SESSION_ID, 15000L, PlayerStatus.PLAYING));

        // All three heartbeats should be persisted
        List<Heartbeat> heartbeats = mongoTemplate.findAll(Heartbeat.class);
        assertEquals(3, heartbeats.size());

        // Progress should hold the latest playerTime
        Progress progress = progressService.getProgress(VIDEO_ID, testUser.getId(), CLIENT_ID);
        assertNotNull(progress);
        assertEquals(15000L, progress.getProgressInMillis());

        // Only one progress document should exist (upsert, not insert each time)
        long progressCount = mongoTemplate.count(new Query(), Progress.class);
        assertEquals(1, progressCount);
    }

    // --- Get progress: correct progressInMillis ---

    @Test
    void getProgress_returnsCorrectMillis() {
        heartbeatService.add(buildHeartbeat(VIDEO_ID, CLIENT_ID, SESSION_ID, 42000L, PlayerStatus.PLAYING));

        Progress progress = progressService.getProgress(VIDEO_ID, testUser.getId(), CLIENT_ID);
        assertNotNull(progress);
        assertEquals(42000L, progress.getProgressInMillis());
        assertEquals(VIDEO_ID, progress.getVideoId());
    }

    // --- Get progress for non-existent video: null ---

    @Test
    void getProgress_nonExistentVideo_returnsNull() {
        Progress progress = progressService.getProgress("v_does_not_exist", testUser.getId(), CLIENT_ID);
        assertNull(progress);
    }

    // --- Multiple viewers for same video: separate progress per viewer ---

    @Test
    void multipleViewers_separateProgressPerViewer() {
        User viewer1 = testUser;
        User viewer2 = new User();
        viewer2.setId(new ObjectId().toHexString());
        viewer2.setPhone("13800000002");
        mongoTemplate.save(viewer2);

        // Viewer 1 watches the video
        UserHolder.set(viewer1);
        heartbeatService.add(buildHeartbeat(VIDEO_ID, CLIENT_ID, SESSION_ID, 10000L, PlayerStatus.PLAYING));

        // Viewer 2 watches the same video
        UserHolder.set(viewer2);
        String clientId2 = "client_test_002";
        heartbeatService.add(buildHeartbeat(VIDEO_ID, clientId2, "session_002", 25000L, PlayerStatus.PLAYING));

        // Each viewer should have independent progress
        Progress p1 = progressService.getProgress(VIDEO_ID, viewer1.getId(), CLIENT_ID);
        assertNotNull(p1);
        assertEquals(10000L, p1.getProgressInMillis());
        assertEquals(viewer1.getId(), p1.getViewerId());

        Progress p2 = progressService.getProgress(VIDEO_ID, viewer2.getId(), clientId2);
        assertNotNull(p2);
        assertEquals(25000L, p2.getProgressInMillis());
        assertEquals(viewer2.getId(), p2.getViewerId());

        // Two distinct progress records
        long count = mongoTemplate.count(new Query(Criteria.where("videoId").is(VIDEO_ID)), Progress.class);
        assertEquals(2, count);
    }

    // --- Different clientIds with same viewer: progress is grouped by viewerId ---

    @Test
    void differentClientIds_sameViewer_progressGroupedByViewer() {
        String clientA = "client_browser";
        String clientB = "client_mobile";

        // Same viewer, different clients — second heartbeat should update the same progress
        heartbeatService.add(buildHeartbeat(VIDEO_ID, clientA, "sess_a", 7000L, PlayerStatus.PLAYING));
        heartbeatService.add(buildHeartbeat(VIDEO_ID, clientB, "sess_b", 20000L, PlayerStatus.PLAYING));

        // When viewerId is present, ProgressRepository queries by (videoId, viewerId) only,
        // so the second heartbeat updates the existing progress rather than creating a new one.
        List<Progress> allProgress = mongoTemplate.find(
                new Query(Criteria.where("videoId").is(VIDEO_ID)
                        .and("viewerId").is(testUser.getId())),
                Progress.class);
        assertEquals(1, allProgress.size());

        // The latest playerTime should win
        assertEquals(20000L, allProgress.get(0).getProgressInMillis());
    }

    // --- Heartbeat without logged-in user (anonymous viewer) ---

    @Test
    void heartbeat_anonymousViewer_usesClientIdForProgress() {
        UserHolder.remove();

        Heartbeat heartbeat = buildHeartbeat(VIDEO_ID, CLIENT_ID, SESSION_ID, 9000L, PlayerStatus.PLAYING);
        heartbeatService.add(heartbeat);

        // Heartbeat saved with null viewerId
        List<Heartbeat> heartbeats = mongoTemplate.findAll(Heartbeat.class);
        assertEquals(1, heartbeats.size());
        assertNull(heartbeats.get(0).getViewerId());

        // Progress should be retrievable by clientId (viewerId is null)
        Progress progress = progressService.getProgress(VIDEO_ID, null, CLIENT_ID);
        assertNotNull(progress);
        assertEquals(9000L, progress.getProgressInMillis());
        assertEquals(CLIENT_ID, progress.getClientId());
        assertNull(progress.getViewerId());
    }
}
