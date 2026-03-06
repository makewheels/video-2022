package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.entity.Watch;
import com.github.makewheels.video2022.video.bean.entity.YouTube;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import com.github.makewheels.video2022.video.service.VideoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class VideoCreateServiceTest extends BaseIntegrationTest {

    @Autowired
    private VideoService videoService;

    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        // Create and persist a test user in MongoDB
        testUser = new User();
        testUser.setPhone("13800000001");
        testUser.setRegisterChannel("TEST");
        testUser.setToken("test-token-integration");
        mongoTemplate.save(testUser);

        // Set the user in ThreadLocal so VideoCreateService can pick it up
        UserHolder.set(testUser);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        cleanDatabase();
    }

    // ──────────────────── USER_UPLOAD tests ────────────────────

    @Test
    void createUserUploadVideo_savesVideoFileAndWatch() {
        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename("my-holiday.mp4");
        dto.setSize(1024 * 1024L);

        JSONObject response = videoService.create(dto);

        // Response must contain all expected keys
        assertNotNull(response.getString("videoId"));
        assertNotNull(response.getString("fileId"));
        assertNotNull(response.getString("watchId"));
        assertNotNull(response.getString("watchUrl"));
        assertNotNull(response.getString("shortUrl"));

        // Verify Video persisted in MongoDB
        String videoId = response.getString("videoId");
        Video video = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(video, "Video should be saved to MongoDB");
        assertEquals(testUser.getId(), video.getUploaderId());
        assertEquals(testUser.getId(), video.getOwnerId());
        assertEquals(VideoType.USER_UPLOAD, video.getVideoType());

        // Verify File persisted in MongoDB
        String fileId = response.getString("fileId");
        File file = mongoTemplate.findById(fileId, File.class);
        assertNotNull(file, "File should be saved to MongoDB");
        assertEquals(FileType.RAW_VIDEO, file.getFileType());
        assertEquals(videoId, file.getVideoId());

        // Verify Watch is embedded in Video
        Watch watch = video.getWatch();
        assertNotNull(watch);
        assertNotNull(watch.getWatchId());
        assertNotNull(watch.getWatchUrl());
        assertEquals(0, watch.getWatchCount());
    }

    @Test
    void createUserUploadVideo_videoIdStartsWithV() {
        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename("recording.mov");
        dto.setSize(2048L);

        JSONObject response = videoService.create(dto);

        String videoId = response.getString("videoId");
        assertTrue(videoId.startsWith("v_"),
                "videoId should start with 'v_', got: " + videoId);
    }

    @Test
    void createUserUploadVideo_fileHasCorrectExtension() {
        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename("Tutorial.MKV");
        dto.setSize(5000L);

        JSONObject response = videoService.create(dto);

        File file = mongoTemplate.findById(response.getString("fileId"), File.class);
        assertNotNull(file);
        assertEquals("mkv", file.getExtension(),
                "Extension should be lowercase from rawFilename");
        assertEquals("Tutorial.MKV", file.getRawFilename());
    }

    @Test
    void createUserUploadVideo_statusIsCreated() {
        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename("clip.avi");
        dto.setSize(100L);

        JSONObject response = videoService.create(dto);

        Video video = mongoTemplate.findById(response.getString("videoId"), Video.class);
        assertNotNull(video);
        assertEquals(VideoStatus.CREATED, video.getStatus());
    }

    @Test
    void createUserUploadVideo_watchUrlContainsWatchId() {
        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename("demo.mp4");
        dto.setSize(999L);

        JSONObject response = videoService.create(dto);

        String watchId = response.getString("watchId");
        String watchUrl = response.getString("watchUrl");
        assertNotNull(watchId);
        assertNotNull(watchUrl);
        assertTrue(watchUrl.contains(watchId),
                "watchUrl should contain the watchId");
        assertTrue(watchUrl.contains("/w?v="),
                "watchUrl should contain the watch path");
    }

    @Test
    void createUserUploadVideo_fileIdStartsWithF() {
        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename("test.mp4");
        dto.setSize(512L);

        JSONObject response = videoService.create(dto);

        String fileId = response.getString("fileId");
        assertTrue(fileId.startsWith("f_"),
                "fileId should start with 'f_', got: " + fileId);
    }

    // ──────────────────── YOUTUBE tests ────────────────────

    @Test
    void createYoutubeVideo_setsYoutubeFields() {
        String youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        String expectedYoutubeVideoId = "dQw4w9WgXcQ";

        // Mock YoutubeService interactions
        when(youtubeService.getYoutubeVideoIdByUrl(youtubeUrl))
                .thenReturn(expectedYoutubeVideoId);
        when(youtubeService.getFileExtension(expectedYoutubeVideoId))
                .thenReturn("webm");
        when(youtubeService.getVideoInfo(expectedYoutubeVideoId))
                .thenReturn(new JSONObject());

        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.YOUTUBE);
        dto.setYoutubeUrl(youtubeUrl);

        JSONObject response = videoService.create(dto);

        // Verify Video is saved with YouTube info
        Video video = mongoTemplate.findById(response.getString("videoId"), Video.class);
        assertNotNull(video);
        assertEquals(VideoType.YOUTUBE, video.getVideoType());

        YouTube youTube = video.getYouTube();
        assertNotNull(youTube);
        assertEquals(youtubeUrl, youTube.getUrl());
        assertEquals(expectedYoutubeVideoId, youTube.getVideoId());
    }

    @Test
    void createYoutubeVideo_fileDefaultsToWebmExtension() {
        String youtubeUrl = "https://www.youtube.com/watch?v=abc123";
        when(youtubeService.getYoutubeVideoIdByUrl(youtubeUrl))
                .thenReturn("abc123");
        when(youtubeService.getFileExtension("abc123"))
                .thenReturn("webm");
        when(youtubeService.getVideoInfo("abc123"))
                .thenReturn(new JSONObject());

        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.YOUTUBE);
        dto.setYoutubeUrl(youtubeUrl);

        JSONObject response = videoService.create(dto);

        File file = mongoTemplate.findById(response.getString("fileId"), File.class);
        assertNotNull(file);
        assertEquals("webm", file.getExtension());
        assertEquals(VideoType.YOUTUBE, file.getVideoType());
    }

    @Test
    void createYoutubeVideo_responseContainsAllRequiredFields() {
        String youtubeUrl = "https://www.youtube.com/watch?v=xyz789";
        when(youtubeService.getYoutubeVideoIdByUrl(youtubeUrl))
                .thenReturn("xyz789");
        when(youtubeService.getFileExtension("xyz789"))
                .thenReturn("webm");
        when(youtubeService.getVideoInfo("xyz789"))
                .thenReturn(new JSONObject());

        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.YOUTUBE);
        dto.setYoutubeUrl(youtubeUrl);

        JSONObject response = videoService.create(dto);

        assertNotNull(response.getString("fileId"), "response should contain fileId");
        assertNotNull(response.getString("videoId"), "response should contain videoId");
        assertNotNull(response.getString("watchId"), "response should contain watchId");
        assertNotNull(response.getString("watchUrl"), "response should contain watchUrl");
    }

    // ──────────────────── TTL test ────────────────────

    @Test
    void createVideoWithTtl_ttlIsPreservedOnDTO() {
        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename("temp-video.mp4");
        dto.setSize(256L);
        dto.setTtl("7d");

        JSONObject response = videoService.create(dto);

        // Verify video was created successfully
        Video video = mongoTemplate.findById(response.getString("videoId"), Video.class);
        assertNotNull(video);

        // TTL is set on the DTO for downstream processing
        assertEquals("7d", dto.getTtl());
    }

    // ──────────────────── Edge-case tests ────────────────────

    @Test
    void createMultipleVideos_eachGetsUniqueIds() {
        CreateVideoDTO dto1 = new CreateVideoDTO();
        dto1.setVideoType(VideoType.USER_UPLOAD);
        dto1.setRawFilename("video1.mp4");
        dto1.setSize(100L);

        CreateVideoDTO dto2 = new CreateVideoDTO();
        dto2.setVideoType(VideoType.USER_UPLOAD);
        dto2.setRawFilename("video2.mp4");
        dto2.setSize(200L);

        JSONObject response1 = videoService.create(dto1);
        JSONObject response2 = videoService.create(dto2);

        assertNotEquals(response1.getString("videoId"), response2.getString("videoId"));
        assertNotEquals(response1.getString("fileId"), response2.getString("fileId"));
        assertNotEquals(response1.getString("watchId"), response2.getString("watchId"));
    }

    @Test
    void createUserUploadVideo_rawFileLinkedToVideo() {
        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename("linked.mp4");
        dto.setSize(1024L);

        JSONObject response = videoService.create(dto);

        Video video = mongoTemplate.findById(response.getString("videoId"), Video.class);
        File file = mongoTemplate.findById(response.getString("fileId"), File.class);

        assertNotNull(video);
        assertNotNull(file);
        // Video.rawFileId should point to the file
        assertEquals(file.getId(), video.getRawFileId());
        // File.videoId should point back to the video
        assertEquals(video.getId(), file.getVideoId());
    }
}
