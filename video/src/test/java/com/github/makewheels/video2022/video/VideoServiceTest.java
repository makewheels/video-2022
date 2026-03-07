package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.dto.UpdateVideoInfoDTO;
import com.github.makewheels.video2022.video.bean.dto.UpdateWatchSettingsDTO;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.vo.VideoListVO;
import com.github.makewheels.video2022.video.bean.vo.VideoVO;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import com.github.makewheels.video2022.video.service.VideoService;
import com.github.makewheels.video2022.system.response.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link VideoService} — update, detail, list, watch-settings.
 * Creation tests live in {@link VideoCreateServiceTest}.
 */
class VideoServiceTest extends BaseIntegrationTest {

    @Autowired
    private VideoService videoService;

    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        testUser = new User();
        testUser.setPhone("13800000002");
        testUser.setRegisterChannel("TEST");
        testUser.setToken("test-token-video-service");
        mongoTemplate.save(testUser);
        UserHolder.set(testUser);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        cleanDatabase();
    }

    private JSONObject createDefaultVideo(String filename) {
        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename(filename);
        dto.setSize(1024L);
        return videoService.create(dto);
    }

    // ──────────────────── updateVideo tests ────────────────────

    @Test
    void updateVideo_changesTitleAndDescription() {
        JSONObject resp = createDefaultVideo("original.mp4");
        String videoId = resp.getString("videoId");

        UpdateVideoInfoDTO dto = new UpdateVideoInfoDTO();
        dto.setId(videoId);
        dto.setTitle("New Title");
        dto.setDescription("New Description");

        Video updated = videoService.updateVideo(dto);

        assertEquals("New Title", updated.getTitle());
        assertEquals("New Description", updated.getDescription());

        Video fromDb = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(fromDb);
        assertEquals("New Title", fromDb.getTitle());
        assertEquals("New Description", fromDb.getDescription());
    }

    @Test
    void updateVideo_preservesOtherFields() {
        JSONObject resp = createDefaultVideo("preserve.mp4");
        String videoId = resp.getString("videoId");

        UpdateVideoInfoDTO dto = new UpdateVideoInfoDTO();
        dto.setId(videoId);
        dto.setTitle("Updated Title");
        dto.setDescription("Updated Desc");

        videoService.updateVideo(dto);

        Video fromDb = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(fromDb);
        assertEquals(VideoType.USER_UPLOAD, fromDb.getVideoType());
        assertEquals(VideoStatus.CREATED, fromDb.getStatus());
        assertEquals(testUser.getId(), fromDb.getUploaderId());
    }

    @Test
    void updateVideo_canClearDescription() {
        JSONObject resp = createDefaultVideo("clear.mp4");
        String videoId = resp.getString("videoId");

        UpdateVideoInfoDTO dto = new UpdateVideoInfoDTO();
        dto.setId(videoId);
        dto.setTitle("Keep Title");
        dto.setDescription(null);

        videoService.updateVideo(dto);

        Video fromDb = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(fromDb);
        assertEquals("Keep Title", fromDb.getTitle());
        assertNull(fromDb.getDescription());
    }

    // ──────────────────── getVideoDetail tests ────────────────────

    @Test
    void getVideoDetail_returnsCorrectFields() {
        JSONObject resp = createDefaultVideo("detail.mp4");
        String videoId = resp.getString("videoId");

        UpdateVideoInfoDTO dto = new UpdateVideoInfoDTO();
        dto.setId(videoId);
        dto.setTitle("Detail Test");
        dto.setDescription("Detail Description");
        videoService.updateVideo(dto);

        VideoVO vo = videoService.getVideoDetail(videoId);

        assertEquals(videoId, vo.getId());
        assertEquals(testUser.getId(), vo.getUserId());
        assertEquals(VideoType.USER_UPLOAD, vo.getType());
        assertEquals(VideoStatus.CREATED, vo.getStatus());
        assertEquals("Detail Test", vo.getTitle());
        assertEquals("Detail Description", vo.getDescription());
        assertEquals(0, vo.getWatchCount());
        assertNotNull(vo.getWatchId());
        assertNotNull(vo.getWatchUrl());
        assertNotNull(vo.getCreateTime());
        assertNotNull(vo.getCreateTimeString());
    }

    @Test
    void getVideoDetail_watchFieldsMatchCreation() {
        JSONObject resp = createDefaultVideo("watch.mp4");
        String videoId = resp.getString("videoId");
        String watchId = resp.getString("watchId");
        String watchUrl = resp.getString("watchUrl");

        VideoVO vo = videoService.getVideoDetail(videoId);

        assertEquals(watchId, vo.getWatchId());
        assertEquals(watchUrl, vo.getWatchUrl());
    }

    @Test
    void getVideoDetail_coverUrlIsNullWhenNoCover() {
        JSONObject resp = createDefaultVideo("no-cover.mp4");
        String videoId = resp.getString("videoId");

        VideoVO vo = videoService.getVideoDetail(videoId);

        assertNull(vo.getCoverUrl(), "coverUrl should be null when no cover is set");
    }

    // ──────────────────── getMyVideoList tests ────────────────────

    @Test
    void getMyVideoList_returnsCreatedVideos() {
        createDefaultVideo("list1.mp4");
        createDefaultVideo("list2.mp4");
        createDefaultVideo("list3.mp4");

        Result<VideoListVO> result = videoService.getMyVideoList(0, 10);
        assertNotNull(result.getData());
        assertEquals(3, result.getData().getList().size());
    }

    @Test
    void getMyVideoList_paginationSkip() {
        createDefaultVideo("skip1.mp4");
        createDefaultVideo("skip2.mp4");
        createDefaultVideo("skip3.mp4");

        Result<VideoListVO> result = videoService.getMyVideoList(1, 10);
        assertNotNull(result.getData());
        assertEquals(2, result.getData().getList().size());
    }

    @Test
    void getMyVideoList_paginationLimit() {
        createDefaultVideo("lim1.mp4");
        createDefaultVideo("lim2.mp4");
        createDefaultVideo("lim3.mp4");

        Result<VideoListVO> result = videoService.getMyVideoList(0, 2);
        assertNotNull(result.getData());
        assertEquals(2, result.getData().getList().size());
    }

    @Test
    void getMyVideoList_emptyWhenNoVideos() {
        Result<VideoListVO> result = videoService.getMyVideoList(0, 10);
        assertNotNull(result.getData());
        assertTrue(result.getData().getList().isEmpty());
    }

    @Test
    void getMyVideoList_doesNotReturnOtherUsersVideos() {
        createDefaultVideo("my-video.mp4");

        // Switch to a different user
        User otherUser = new User();
        otherUser.setPhone("13800000099");
        otherUser.setRegisterChannel("TEST");
        otherUser.setToken("test-token-other");
        mongoTemplate.save(otherUser);
        UserHolder.set(otherUser);

        Result<VideoListVO> result = videoService.getMyVideoList(0, 10);
        assertNotNull(result.getData());
        assertTrue(result.getData().getList().isEmpty(),
                "Should not return videos belonging to a different user");
    }

    // ──────────────────── updateWatchSettings tests ────────────────────

    @Test
    void updateWatchSettings_changesShowWatchCount() {
        JSONObject resp = createDefaultVideo("settings.mp4");
        String videoId = resp.getString("videoId");

        UpdateWatchSettingsDTO dto = new UpdateWatchSettingsDTO();
        dto.setVideoId(videoId);
        dto.setShowWatchCount(false);

        videoService.updateWatchSettings(dto);

        Video fromDb = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(fromDb);
        assertFalse(fromDb.getWatch().getShowWatchCount());
        assertTrue(fromDb.getWatch().getShowUploadTime(),
                "showUploadTime should remain unchanged");
    }

    @Test
    void updateWatchSettings_changesShowUploadTime() {
        JSONObject resp = createDefaultVideo("time.mp4");
        String videoId = resp.getString("videoId");

        UpdateWatchSettingsDTO dto = new UpdateWatchSettingsDTO();
        dto.setVideoId(videoId);
        dto.setShowUploadTime(false);

        videoService.updateWatchSettings(dto);

        Video fromDb = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(fromDb);
        assertFalse(fromDb.getWatch().getShowUploadTime());
        assertTrue(fromDb.getWatch().getShowWatchCount(),
                "showWatchCount should remain unchanged");
    }

    @Test
    void updateWatchSettings_nullFieldsNotOverwritten() {
        JSONObject resp = createDefaultVideo("null-test.mp4");
        String videoId = resp.getString("videoId");

        // First set both to false
        UpdateWatchSettingsDTO dto1 = new UpdateWatchSettingsDTO();
        dto1.setVideoId(videoId);
        dto1.setShowWatchCount(false);
        dto1.setShowUploadTime(false);
        videoService.updateWatchSettings(dto1);

        // Then update only showWatchCount, leaving showUploadTime null
        UpdateWatchSettingsDTO dto2 = new UpdateWatchSettingsDTO();
        dto2.setVideoId(videoId);
        dto2.setShowWatchCount(true);
        videoService.updateWatchSettings(dto2);

        Video fromDb = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(fromDb);
        assertTrue(fromDb.getWatch().getShowWatchCount());
        assertFalse(fromDb.getWatch().getShowUploadTime(),
                "showUploadTime should remain false (not overwritten by null)");
    }
}
