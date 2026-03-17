package com.github.makewheels.video2022.watch;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.watch.play.WatchLog;
import com.github.makewheels.video2022.watch.watchhistory.WatchHistoryRepository;
import com.github.makewheels.video2022.watch.watchhistory.WatchHistoryService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WatchHistoryTest extends BaseIntegrationTest {

    @Autowired
    private WatchHistoryService watchHistoryService;

    @Autowired
    private WatchHistoryRepository watchHistoryRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        testUser = new User();
        testUser.setId(new ObjectId().toHexString());
        testUser.setPhone("13800000099");
        mongoTemplate.save(testUser);
        UserHolder.set(testUser);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    private WatchLog createWatchLog(String videoId) {
        WatchLog log = new WatchLog();
        log.setVideoId(videoId);
        log.setViewerId(testUser.getId());
        log.setCreateTime(new Date());
        return mongoTemplate.save(log);
    }

    private Video createVideo(String videoId, String title) {
        Video video = new Video();
        video.setId(videoId);
        video.setTitle(title);
        video.setUploaderId(testUser.getId());
        return mongoTemplate.save(video);
    }

    @Test
    void getMyHistory_returnsWatchedVideos() {
        String videoId = new ObjectId().toHexString();
        createVideo(videoId, "测试视频");
        createWatchLog(videoId);

        var result = watchHistoryService.getMyHistory(testUser.getId(), 0, 20);
        assertNotNull(result.getData());
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) result.getData().get("list");
        assertEquals(1, list.size());
        assertEquals(1L, result.getData().get("total"));
    }

    @Test
    void getMyHistory_pagination() {
        for (int i = 0; i < 3; i++) {
            String videoId = new ObjectId().toHexString();
            createVideo(videoId, "视频" + i);
            createWatchLog(videoId);
        }

        var page0 = watchHistoryService.getMyHistory(testUser.getId(), 0, 2);
        @SuppressWarnings("unchecked")
        List<Object> list0 = (List<Object>) page0.getData().get("list");
        assertEquals(2, list0.size());
        assertEquals(3L, page0.getData().get("total"));

        var page1 = watchHistoryService.getMyHistory(testUser.getId(), 1, 2);
        @SuppressWarnings("unchecked")
        List<Object> list1 = (List<Object>) page1.getData().get("list");
        assertEquals(1, list1.size());
    }

    @Test
    void getMyHistory_empty() {
        var result = watchHistoryService.getMyHistory(testUser.getId(), 0, 20);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) result.getData().get("list");
        assertEquals(0, list.size());
        assertEquals(0L, result.getData().get("total"));
    }

    @Test
    void clearHistory_removesAllLogs() {
        createWatchLog(new ObjectId().toHexString());
        createWatchLog(new ObjectId().toHexString());

        assertEquals(2, watchHistoryRepository.countByViewerId(testUser.getId()));
        watchHistoryService.clearHistory(testUser.getId());
        assertEquals(0, watchHistoryRepository.countByViewerId(testUser.getId()));
    }

    @Test
    void clearHistory_doesNotAffectOtherUsers() {
        createWatchLog(new ObjectId().toHexString());

        WatchLog otherLog = new WatchLog();
        otherLog.setVideoId(new ObjectId().toHexString());
        otherLog.setViewerId("other-user-id");
        otherLog.setCreateTime(new Date());
        mongoTemplate.save(otherLog);

        watchHistoryService.clearHistory(testUser.getId());
        assertEquals(0, watchHistoryRepository.countByViewerId(testUser.getId()));
        assertEquals(1, watchHistoryRepository.countByViewerId("other-user-id"));
    }
}
