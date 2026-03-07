package com.github.makewheels.video2022.watch;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.watch.playback.PlaybackSession;
import com.github.makewheels.video2022.watch.playback.PlaybackService;
import com.github.makewheels.video2022.watch.playback.PlaybackSessionRepository;
import com.github.makewheels.video2022.watch.playback.dto.ExitPlaybackDTO;
import com.github.makewheels.video2022.watch.playback.dto.HeartbeatPlaybackDTO;
import com.github.makewheels.video2022.watch.playback.dto.StartPlaybackDTO;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaybackServiceTest extends BaseIntegrationTest {

    @Autowired
    private PlaybackService playbackService;

    @Autowired
    private PlaybackSessionRepository playbackSessionRepository;

    private final List<String> createdSessionIds = new ArrayList<>();
    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        createdSessionIds.clear();

        testUser = new User();
        testUser.setId(new ObjectId().toHexString());
        testUser.setPhone("13800000001");
        mongoTemplate.save(testUser);

        UserHolder.set(testUser);
    }

    @AfterEach
    void tearDown() {
        for (String id : createdSessionIds) {
            mongoTemplate.remove(Query.query(Criteria.where("_id").is(id)), PlaybackSession.class);
        }
        UserHolder.remove();
    }

    private StartPlaybackDTO buildStartDTO() {
        StartPlaybackDTO dto = new StartPlaybackDTO();
        dto.setWatchId("w_test");
        dto.setVideoId("v_test");
        dto.setClientId("c_test");
        dto.setSessionId("s_test");
        return dto;
    }

    @Test
    void startSession_createsSessionWithInitialValues() {
        PlaybackSession session = playbackService.startSession(buildStartDTO());
        createdSessionIds.add(session.getId());

        assertNotNull(session.getId());
        assertEquals("v_test", session.getVideoId());
        assertEquals("w_test", session.getWatchId());
        assertEquals(0L, session.getTotalPlayDurationMs());
        assertEquals(0L, session.getMaxProgressMs());
        assertEquals("PLAYING", session.getExitType());
        assertNotNull(session.getStartTime());
    }

    @Test
    void startSession_persistsToMongoDB() {
        PlaybackSession session = playbackService.startSession(buildStartDTO());
        createdSessionIds.add(session.getId());

        PlaybackSession found = playbackSessionRepository.getById(session.getId());
        assertNotNull(found);
        assertEquals(session.getVideoId(), found.getVideoId());
    }

    @Test
    void heartbeat_updatesProgressAndCount() {
        PlaybackSession session = playbackService.startSession(buildStartDTO());
        createdSessionIds.add(session.getId());

        HeartbeatPlaybackDTO dto = new HeartbeatPlaybackDTO();
        dto.setPlaybackSessionId(session.getId());
        dto.setCurrentTimeMs(30000L);
        dto.setIsPlaying(true);
        dto.setResolution("720p");
        dto.setTotalPlayDurationMs(25000L);

        playbackService.heartbeat(dto);

        PlaybackSession updated = playbackSessionRepository.getById(session.getId());
        assertEquals(30000L, updated.getCurrentProgressMs());
        assertEquals(30000L, updated.getMaxProgressMs());
        assertEquals(25000L, updated.getTotalPlayDurationMs());
        assertEquals("720p", updated.getResolution());
        assertEquals(1, updated.getHeartbeatCount());
    }

    @Test
    void heartbeat_multipleUpdates_tracksMaxProgress() {
        PlaybackSession session = playbackService.startSession(buildStartDTO());
        createdSessionIds.add(session.getId());

        HeartbeatPlaybackDTO dto1 = new HeartbeatPlaybackDTO();
        dto1.setPlaybackSessionId(session.getId());
        dto1.setCurrentTimeMs(50000L);
        dto1.setTotalPlayDurationMs(45000L);
        playbackService.heartbeat(dto1);

        // User seeks backward
        HeartbeatPlaybackDTO dto2 = new HeartbeatPlaybackDTO();
        dto2.setPlaybackSessionId(session.getId());
        dto2.setCurrentTimeMs(20000L);
        dto2.setTotalPlayDurationMs(60000L);
        playbackService.heartbeat(dto2);

        PlaybackSession updated = playbackSessionRepository.getById(session.getId());
        assertEquals(20000L, updated.getCurrentProgressMs());
        assertEquals(50000L, updated.getMaxProgressMs()); // max doesn't go back
        assertEquals(2, updated.getHeartbeatCount());
    }

    @Test
    void heartbeat_nonExistentSession_doesNotThrow() {
        HeartbeatPlaybackDTO dto = new HeartbeatPlaybackDTO();
        dto.setPlaybackSessionId("non_existent_id");
        dto.setCurrentTimeMs(10000L);
        assertDoesNotThrow(() -> playbackService.heartbeat(dto));
    }

    @Test
    void exit_setsEndTimeAndExitType() {
        PlaybackSession session = playbackService.startSession(buildStartDTO());
        createdSessionIds.add(session.getId());

        ExitPlaybackDTO dto = new ExitPlaybackDTO();
        dto.setPlaybackSessionId(session.getId());
        dto.setCurrentTimeMs(120000L);
        dto.setTotalPlayDurationMs(100000L);
        dto.setExitType("CLOSE_TAB");
        dto.setResolution("1080p");

        playbackService.exit(dto);

        PlaybackSession updated = playbackSessionRepository.getById(session.getId());
        assertNotNull(updated.getEndTime());
        assertEquals("CLOSE_TAB", updated.getExitType());
        assertEquals(120000L, updated.getCurrentProgressMs());
        assertEquals(100000L, updated.getTotalPlayDurationMs());
        assertEquals("1080p", updated.getResolution());
    }

    @Test
    void exit_nonExistentSession_doesNotThrow() {
        ExitPlaybackDTO dto = new ExitPlaybackDTO();
        dto.setPlaybackSessionId("non_existent_id");
        assertDoesNotThrow(() -> playbackService.exit(dto));
    }

    @Test
    void fullLifecycle_startHeartbeatExit() {
        // Start
        PlaybackSession session = playbackService.startSession(buildStartDTO());
        createdSessionIds.add(session.getId());

        // 3 heartbeats
        for (int i = 1; i <= 3; i++) {
            HeartbeatPlaybackDTO hb = new HeartbeatPlaybackDTO();
            hb.setPlaybackSessionId(session.getId());
            hb.setCurrentTimeMs((long) i * 15000);
            hb.setIsPlaying(true);
            hb.setTotalPlayDurationMs((long) i * 14000);
            playbackService.heartbeat(hb);
        }

        // Exit
        ExitPlaybackDTO exit = new ExitPlaybackDTO();
        exit.setPlaybackSessionId(session.getId());
        exit.setCurrentTimeMs(48000L);
        exit.setTotalPlayDurationMs(42000L);
        exit.setExitType("NAVIGATE_AWAY");
        playbackService.exit(exit);

        PlaybackSession result = playbackSessionRepository.getById(session.getId());
        assertEquals(3, result.getHeartbeatCount());
        assertEquals(48000L, result.getCurrentProgressMs());
        assertEquals(48000L, result.getMaxProgressMs()); // exit also updates maxProgressMs
        assertEquals("NAVIGATE_AWAY", result.getExitType());
        assertNotNull(result.getEndTime());
    }
}
