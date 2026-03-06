package com.github.makewheels.video2022.cover;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.models.QuerySnapshotJobListResponseBody;
import com.aliyun.mts20140618.models.SubmitSnapshotJobResponse;
import com.aliyun.mts20140618.models.SubmitSnapshotJobResponseBody;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.file.constants.ObjectStorageProvider;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.entity.YouTube;
import com.github.makewheels.video2022.video.constants.VideoType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoverServiceTest extends BaseIntegrationTest {

    @Autowired
    private CoverLauncher coverLauncher;

    @Autowired
    private CoverCallbackService coverCallbackService;

    @Autowired
    private CoverService coverService;

    @Autowired
    private CoverRepository coverRepository;

    private User testUser;
    private Video testVideo;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        cleanRedisKeys("video-2022:*");

        testUser = new User();
        testUser.setPhone("13800000001");
        testUser.setRegisterChannel("TEST");
        testUser.setToken("test-token-cover");
        mongoTemplate.save(testUser);

        testVideo = new Video();
        testVideo.setUploaderId(testUser.getId());
        testVideo.setOwnerId(testUser.getId());
        testVideo.setTitle("Test Video for Cover");
        testVideo.setVideoType(VideoType.USER_UPLOAD);
        testVideo.setProvider(ObjectStorageProvider.ALIYUN_OSS);
        testVideo.setCreateTime(new Date());

        // Create a raw file so fileService.getKeyByFileId works
        File rawFile = new File();
        rawFile.setUploaderId(testUser.getId());
        rawFile.setFileType(FileType.RAW_VIDEO);
        rawFile.setKey("videos/test/raw/raw-file.mp4");
        rawFile.setExtension("mp4");
        mongoTemplate.save(rawFile);

        testVideo.setRawFileId(rawFile.getId());
        mongoTemplate.save(testVideo);

        // Mock ossVideoService.getObject() so aliyunCoverCallback → file.setObjectInfo() doesn't NPE
        when(ossVideoService.getObject(anyString())).thenAnswer(invocation -> {
            String inputKey = invocation.getArgument(0);
            OSSObject ossObject = new OSSObject();
            ossObject.setKey(inputKey);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setHeader("ETag", "\"mock-etag\"");
            metadata.setContentLength(1024L);
            metadata.setLastModified(new Date());
            metadata.setHeader("x-oss-storage-class", "Standard");
            ossObject.setObjectMetadata(metadata);
            return ossObject;
        });
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    /**
     * Build mock responses for AliyunMpsService.submitSnapshotJob and simpleQueryOneJob.
     */
    private void mockAliyunMpsForSnapshot(String fakeJobId, String jobState) {
        SubmitSnapshotJobResponse submitResponse = new SubmitSnapshotJobResponse();
        SubmitSnapshotJobResponseBody body = new SubmitSnapshotJobResponseBody();
        SubmitSnapshotJobResponseBody.SubmitSnapshotJobResponseBodySnapshotJob snapshotJob =
                new SubmitSnapshotJobResponseBody.SubmitSnapshotJobResponseBodySnapshotJob();
        snapshotJob.setId(fakeJobId);
        body.setSnapshotJob(snapshotJob);
        submitResponse.setBody(body);
        when(aliyunMpsService.submitSnapshotJob(anyString(), anyString())).thenReturn(submitResponse);

        QuerySnapshotJobListResponseBody.QuerySnapshotJobListResponseBodySnapshotJobListSnapshotJob
                queryJob = new QuerySnapshotJobListResponseBody
                .QuerySnapshotJobListResponseBodySnapshotJobListSnapshotJob();
        queryJob.setState(jobState);
        when(aliyunMpsService.simpleQueryOneJob(fakeJobId)).thenReturn(queryJob);
    }

    // ──────────────────── USER_UPLOAD cover creation ────────────────────

    @Test
    void createCover_userUpload_savesCoverWithStatusCreated() {
        mockAliyunMpsForSnapshot("job-001", "Success");

        coverLauncher.createCover(testUser, testVideo);

        Cover cover = coverRepository.getByVideoId(testVideo.getId());
        assertNotNull(cover, "Cover should be persisted in MongoDB");
        assertEquals(testVideo.getId(), cover.getVideoId());
        assertEquals(testUser.getId(), cover.getUserId());
        assertNotNull(cover.getFileId(), "Cover should reference a File");
        assertNotNull(cover.getCreateTime());
    }

    @Test
    void createCover_userUpload_callsAliyunSubmitSnapshotJob() {
        mockAliyunMpsForSnapshot("job-002", "Success");

        coverLauncher.createCover(testUser, testVideo);

        verify(aliyunMpsService).submitSnapshotJob(anyString(), anyString());
    }

    @Test
    void createCover_userUpload_setsJobIdOnCover() {
        String fakeJobId = "job-003";
        mockAliyunMpsForSnapshot(fakeJobId, "Success");

        coverLauncher.createCover(testUser, testVideo);

        Cover cover = coverRepository.getByVideoId(testVideo.getId());
        assertNotNull(cover);
        assertEquals(fakeJobId, cover.getJobId(),
                "Cover jobId should match the Aliyun MPS job ID");
    }

    @Test
    void createCover_userUpload_updatesCoverStatusFromAliyunJob() {
        mockAliyunMpsForSnapshot("job-004", "Success");

        coverLauncher.createCover(testUser, testVideo);

        Cover cover = coverRepository.getByVideoId(testVideo.getId());
        assertNotNull(cover);
        assertNotNull(cover.getStatus(), "Cover should have a status set by Aliyun job query");
    }

    @Test
    void createCover_userUpload_setsKeyPath() {
        mockAliyunMpsForSnapshot("job-005", "Success");

        coverLauncher.createCover(testUser, testVideo);

        Cover cover = coverRepository.getByVideoId(testVideo.getId());
        assertNotNull(cover);
        assertNotNull(cover.getKey(), "Cover key should be set");
        assertTrue(cover.getKey().contains("/cover/"),
                "Cover key should contain '/cover/' path segment");
        assertTrue(cover.getKey().endsWith(".jpg"),
                "USER_UPLOAD cover key should end with .jpg extension");
    }

    @Test
    void createCover_userUpload_setsAccessUrl() {
        mockAliyunMpsForSnapshot("job-006", "Success");

        coverLauncher.createCover(testUser, testVideo);

        Cover cover = coverRepository.getByVideoId(testVideo.getId());
        assertNotNull(cover);
        assertNotNull(cover.getAccessUrl(), "Cover should have an access URL");
        assertTrue(cover.getAccessUrl().contains(cover.getKey()),
                "Access URL should contain the cover key");
    }

    @Test
    void createCover_userUpload_createsFileWithCoverType() {
        mockAliyunMpsForSnapshot("job-007", "Success");

        coverLauncher.createCover(testUser, testVideo);

        Cover cover = coverRepository.getByVideoId(testVideo.getId());
        assertNotNull(cover);

        File coverFile = mongoTemplate.findById(cover.getFileId(), File.class);
        assertNotNull(coverFile, "Cover file should be persisted in MongoDB");
        assertEquals(FileType.COVER, coverFile.getFileType());
        assertEquals(testVideo.getId(), coverFile.getVideoId());
        assertEquals(testUser.getId(), coverFile.getUploaderId());
    }

    @Test
    void createCover_userUpload_updatesVideoWithCoverId() {
        mockAliyunMpsForSnapshot("job-008", "Success");

        coverLauncher.createCover(testUser, testVideo);

        Video updatedVideo = mongoTemplate.findById(testVideo.getId(), Video.class);
        assertNotNull(updatedVideo);
        assertNotNull(updatedVideo.getCoverId(),
                "Video.coverId should be set after cover creation");

        Cover cover = coverRepository.getByVideoId(testVideo.getId());
        assertEquals(cover.getId(), updatedVideo.getCoverId(),
                "Video.coverId should match the created cover ID");
    }

    @Test
    void createCover_userUpload_coverIdStartsWithC() {
        mockAliyunMpsForSnapshot("job-009", "Success");

        coverLauncher.createCover(testUser, testVideo);

        Cover cover = coverRepository.getByVideoId(testVideo.getId());
        assertNotNull(cover);
        assertTrue(cover.getId().startsWith("c_"),
                "Cover ID should start with 'c_', got: " + cover.getId());
    }

    @Test
    void createCover_userUpload_fileIdStartsWithF() {
        mockAliyunMpsForSnapshot("job-010", "Success");

        coverLauncher.createCover(testUser, testVideo);

        Cover cover = coverRepository.getByVideoId(testVideo.getId());
        assertNotNull(cover);
        assertTrue(cover.getFileId().startsWith("f_"),
                "Cover fileId should start with 'f_', got: " + cover.getFileId());
    }

    // ──────────────────── YouTube cover callback ────────────────────

    @Test
    void youtubeUploadFinishCallback_setsStatusToReady() {
        Cover cover = new Cover();
        cover.setId("c_youtube_test_001");
        cover.setUserId(testUser.getId());
        cover.setVideoId(testVideo.getId());
        cover.setProvider(CoverProvider.YOUTUBE);
        cover.setStatus(CoverStatus.CREATED);
        cover.setKey("videos/test/cover/youtube-thumb.jpg");
        cover.setFileId("f_youtube_file_001");
        mongoTemplate.save(cover);

        Result<Void> result = coverCallbackService.youtubeUploadFinishCallback("c_youtube_test_001");

        assertTrue(result.getCode() == 0, "Callback should succeed");

        Cover updatedCover = mongoTemplate.findById("c_youtube_test_001", Cover.class);
        assertNotNull(updatedCover);
        assertEquals(CoverStatus.READY, updatedCover.getStatus(),
                "Cover status should be READY after YouTube callback");
        assertNotNull(updatedCover.getFinishTime(),
                "Cover finishTime should be set after callback");
    }

    @Test
    void youtubeUploadFinishCallback_withNonExistentCoverId_returnsError() {
        Result<Void> result = coverCallbackService.youtubeUploadFinishCallback("c_nonexistent");

        assertTrue(result.getCode() != 0,
                "Callback with non-existent coverId should return error");
    }

    // ──────────────────── Cover with video that already has a cover ────────────────────

    @Test
    void createCover_videoAlreadyHasCover_overwritesCoverId() {
        mockAliyunMpsForSnapshot("job-first", "Success");
        coverLauncher.createCover(testUser, testVideo);

        Video videoAfterFirst = mongoTemplate.findById(testVideo.getId(), Video.class);
        assertNotNull(videoAfterFirst);
        String firstCoverId = videoAfterFirst.getCoverId();
        assertNotNull(firstCoverId);

        // Reload testVideo from DB to get its current state
        testVideo = mongoTemplate.findById(testVideo.getId(), Video.class);
        assertNotNull(testVideo);

        mockAliyunMpsForSnapshot("job-second", "Success");
        coverLauncher.createCover(testUser, testVideo);

        Video videoAfterSecond = mongoTemplate.findById(testVideo.getId(), Video.class);
        assertNotNull(videoAfterSecond);
        String secondCoverId = videoAfterSecond.getCoverId();
        assertNotNull(secondCoverId);

        assertNotEquals(firstCoverId, secondCoverId,
                "Second cover creation should produce a new coverId");

        List<Cover> allCovers = mongoTemplate.findAll(Cover.class);
        assertTrue(allCovers.size() >= 2,
                "Both old and new covers should exist in MongoDB");
    }

    // ──────────────────── YouTube cover creation ────────────────────

    @Test
    void createCover_youtubeVideo_setsYoutubeProvider() {
        Video youtubeVideo = new Video();
        youtubeVideo.setUploaderId(testUser.getId());
        youtubeVideo.setOwnerId(testUser.getId());
        youtubeVideo.setTitle("YouTube Test Video");
        youtubeVideo.setVideoType(VideoType.YOUTUBE);
        youtubeVideo.setProvider(ObjectStorageProvider.ALIYUN_OSS);
        youtubeVideo.setCreateTime(new Date());

        YouTube youTube = new YouTube();
        youTube.setVideoId("dQw4w9WgXcQ");
        youTube.setUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        JSONObject videoInfo = new JSONObject();
        JSONObject snippet = new JSONObject();
        JSONObject thumbnails = new JSONObject();
        JSONObject standard = new JSONObject();
        standard.put("url", "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg");
        thumbnails.put("standard", standard);
        snippet.put("thumbnails", thumbnails);
        videoInfo.put("snippet", snippet);
        youTube.setVideoInfo(videoInfo);
        youtubeVideo.setYouTube(youTube);

        File rawFile = new File();
        rawFile.setUploaderId(testUser.getId());
        rawFile.setFileType(FileType.RAW_VIDEO);
        rawFile.setKey("videos/test/raw/yt-raw.webm");
        rawFile.setExtension("webm");
        mongoTemplate.save(rawFile);
        youtubeVideo.setRawFileId(rawFile.getId());
        mongoTemplate.save(youtubeVideo);

        coverLauncher.createCover(testUser, youtubeVideo);

        Cover cover = coverRepository.getByVideoId(youtubeVideo.getId());
        assertNotNull(cover, "Cover should be created for YouTube video");
        assertEquals(CoverProvider.YOUTUBE, cover.getProvider(),
                "Cover provider should be YOUTUBE_COVER");
        assertNotNull(cover.getKey(), "Cover key should be set");
        assertEquals("jpg", cover.getExtension(),
                "YouTube cover extension should be extracted from thumbnail URL");

        verify(youtubeService).transferFile(eq(testUser), any(File.class), anyString(), anyString());
    }

    @Test
    void createCover_youtubeVideo_updatesVideoWithCoverId() {
        Video youtubeVideo = new Video();
        youtubeVideo.setUploaderId(testUser.getId());
        youtubeVideo.setOwnerId(testUser.getId());
        youtubeVideo.setTitle("YouTube Cover Link Test");
        youtubeVideo.setVideoType(VideoType.YOUTUBE);
        youtubeVideo.setProvider(ObjectStorageProvider.ALIYUN_OSS);
        youtubeVideo.setCreateTime(new Date());

        YouTube youTube = new YouTube();
        youTube.setVideoId("abc123");
        youTube.setUrl("https://www.youtube.com/watch?v=abc123");
        JSONObject videoInfo = new JSONObject();
        JSONObject snippet = new JSONObject();
        JSONObject thumbnails = new JSONObject();
        JSONObject standard = new JSONObject();
        standard.put("url", "https://i.ytimg.com/vi/abc123/sddefault.jpg");
        thumbnails.put("standard", standard);
        snippet.put("thumbnails", thumbnails);
        videoInfo.put("snippet", snippet);
        youTube.setVideoInfo(videoInfo);
        youtubeVideo.setYouTube(youTube);

        File rawFile = new File();
        rawFile.setUploaderId(testUser.getId());
        rawFile.setFileType(FileType.RAW_VIDEO);
        rawFile.setKey("videos/test/raw/yt-raw2.webm");
        rawFile.setExtension("webm");
        mongoTemplate.save(rawFile);
        youtubeVideo.setRawFileId(rawFile.getId());
        mongoTemplate.save(youtubeVideo);

        coverLauncher.createCover(testUser, youtubeVideo);

        Video updatedVideo = mongoTemplate.findById(youtubeVideo.getId(), Video.class);
        assertNotNull(updatedVideo);
        assertNotNull(updatedVideo.getCoverId(),
                "Video.coverId should be set after YouTube cover creation");
    }
}
