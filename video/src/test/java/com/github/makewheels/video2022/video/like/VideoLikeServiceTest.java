package com.github.makewheels.video2022.video.like;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoType;
import com.github.makewheels.video2022.video.service.VideoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class VideoLikeServiceTest extends BaseIntegrationTest {
    @Autowired
    private VideoLikeService videoLikeService;
    @Autowired
    private VideoService videoService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        User testUser = new User();
        testUser.setPhone("13800000010");
        testUser.setRegisterChannel("TEST");
        testUser.setToken("test-token-like");
        mongoTemplate.save(testUser);
        UserHolder.set(testUser);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        UserHolder.remove();
    }

    private JSONObject createDefaultVideo(String filename) {
        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename(filename);
        dto.setSize(1024L);
        return videoService.create(dto);
    }

    @Test
    void like_increasesLikeCount() {
        JSONObject resp = createDefaultVideo("like-test.mp4");
        String videoId = resp.getString("videoId");

        videoLikeService.react(videoId, LikeType.LIKE);

        Video video = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(video);
        assertEquals(1, video.getLikeCount());
    }

    @Test
    void dislike_increasesDislikeCount() {
        JSONObject resp = createDefaultVideo("dislike-test.mp4");
        String videoId = resp.getString("videoId");

        videoLikeService.react(videoId, LikeType.DISLIKE);

        Video video = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(video);
        assertEquals(1, video.getDislikeCount());
    }

    @Test
    void likeAgain_cancelsLike() {
        JSONObject resp = createDefaultVideo("cancel-test.mp4");
        String videoId = resp.getString("videoId");

        videoLikeService.react(videoId, LikeType.LIKE);
        videoLikeService.react(videoId, LikeType.LIKE);

        Video video = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(video);
        assertEquals(0, video.getLikeCount());
    }

    @Test
    void switchLikeToDislike() {
        JSONObject resp = createDefaultVideo("switch-test.mp4");
        String videoId = resp.getString("videoId");

        videoLikeService.react(videoId, LikeType.LIKE);
        videoLikeService.react(videoId, LikeType.DISLIKE);

        Video video = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(video);
        assertEquals(0, video.getLikeCount());
        assertEquals(1, video.getDislikeCount());
    }

    @Test
    void getLikeStatus_returnsCorrectData() {
        JSONObject resp = createDefaultVideo("status-test.mp4");
        String videoId = resp.getString("videoId");

        videoLikeService.react(videoId, LikeType.LIKE);

        Result<JSONObject> result = videoLikeService.getLikeStatus(videoId);
        JSONObject data = result.getData();
        assertEquals(1, data.getIntValue("likeCount"));
        assertEquals(0, data.getIntValue("dislikeCount"));
        assertEquals("LIKE", data.getString("userAction"));
    }

    @Test
    void getLikeStatus_noAction() {
        JSONObject resp = createDefaultVideo("no-action.mp4");
        String videoId = resp.getString("videoId");

        Result<JSONObject> result = videoLikeService.getLikeStatus(videoId);
        JSONObject data = result.getData();
        assertEquals(0, data.getIntValue("likeCount"));
        assertNull(data.getString("userAction"));
    }
}
