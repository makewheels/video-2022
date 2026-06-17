package com.github.makewheels.video2022.transcode;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.StorageClass;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.contants.Resolution;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.contants.TranscodeStatus;
import com.github.makewheels.video2022.transcode.local.LocalTranscodeService;
import com.github.makewheels.video2022.transcode.local.dto.CreateLocalTranscodeRequest;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.TranscodeMode;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import com.github.makewheels.video2022.video.service.VideoReadyService;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LocalTranscodeServiceTest extends BaseIntegrationTest {

    @Autowired
    private LocalTranscodeService localTranscodeService;

    // Isolate from downstream side-effects (Ding notifications, storage class changes)
    @MockitoBean
    private VideoReadyService videoReadyService;

    private static final String MOCK_PRESIGNED_URL = "http://mock-oss.local/m3u8?token=xxx";

    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        testUser = new User();
        testUser.setPhone("13800000002");
        testUser.setRegisterChannel("TEST");
        testUser.setToken("test-token-local-transcode");
        mongoTemplate.save(testUser);
        UserHolder.set(testUser);

        // Upload credentials are minted via OssVideoService; return a stub
        when(ossVideoService.generateUploadCredentials(anyString())).thenReturn(stubCredentials());
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        cleanDatabase();
    }

    private JSONObject stubCredentials() {
        JSONObject creds = new JSONObject();
        creds.put("bucket", "video-2022-test");
        creds.put("endpoint", "oss-cn-beijing.aliyuncs.com");
        creds.put("accessKeyId", "STS.mockAk");
        creds.put("secretKey", "mockSk");
        creds.put("sessionToken", "mockToken");
        return creds;
    }

    private Video createLocalVideo() {
        Video video = new Video();
        video.setId("v_local");
        video.setUploaderId(testUser.getId());
        video.setVideoType(VideoType.USER_UPLOAD);
        video.setTranscodeMode(TranscodeMode.LOCAL);
        video.setStatus(VideoStatus.PREPARE_TRANSCODING);
        video.setRawFileId("f_raw");
        mongoTemplate.save(video);

        File rawFile = new File();
        rawFile.setId("f_raw");
        rawFile.setVideoId("v_local");
        rawFile.setKey("videos/" + testUser.getId() + "/raw/f_raw.mp4");
        mongoTemplate.save(rawFile);
        return video;
    }

    private CreateLocalTranscodeRequest request(String videoId, String resolution) {
        CreateLocalTranscodeRequest request = new CreateLocalTranscodeRequest();
        request.setVideoId(videoId);
        request.setResolution(resolution);
        request.setWidth(1920);
        request.setHeight(1080);
        request.setDurationMs(60_000L);
        request.setVideoCodec("h264");
        request.setAudioCodec("aac");
        request.setBitrate(738);
        return request;
    }

    private void setupOssMocksForFinish(String m3u8Key, String transcodeId) {
        ObjectMetadata metadata = mock(ObjectMetadata.class);
        when(metadata.getETag()).thenReturn("mock-etag");
        when(metadata.getContentLength()).thenReturn(500L);
        when(metadata.getObjectStorageClass()).thenReturn(StorageClass.Standard);
        when(metadata.getLastModified()).thenReturn(new Date());
        OSSObject ossObject = mock(OSSObject.class);
        when(ossObject.getKey()).thenReturn(m3u8Key);
        when(ossObject.getObjectMetadata()).thenReturn(metadata);
        when(ossVideoService.getObject(m3u8Key)).thenReturn(ossObject);
        when(ossVideoService.generatePresignedUrl(eq(m3u8Key), any(Duration.class)))
                .thenReturn(MOCK_PRESIGNED_URL);

        String folder = FilenameUtils.getPath(m3u8Key);
        List<OSSObjectSummary> summaries = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            OSSObjectSummary summary = new OSSObjectSummary();
            summary.setKey(folder + String.format("%s-%05d.ts", transcodeId, i));
            summary.setETag("etag-" + i);
            summary.setSize(100_000L + i * 1000L);
            summary.setStorageClass("Standard");
            summary.setLastModified(new Date());
            summaries.add(summary);
        }
        when(ossVideoService.listAllObjects(folder)).thenReturn(summaries);
    }

    private String m3u8Content(String transcodeId) {
        return String.join("\n",
                "#EXTM3U", "#EXT-X-VERSION:3", "#EXT-X-TARGETDURATION:7",
                "#EXTINF:6.0,", String.format("%s-00000.ts", transcodeId),
                "#EXTINF:6.0,", String.format("%s-00001.ts", transcodeId),
                "#EXTINF:3.0,", String.format("%s-00002.ts", transcodeId),
                "#EXT-X-ENDLIST");
    }

    @Test
    void createTranscode_registersLocalTranscodeAndMediaInfo() {
        Video video = createLocalVideo();

        JSONObject response = localTranscodeService.createTranscode(request(video.getId(), Resolution.R_1080P));

        String transcodeId = response.getString("transcodeId");
        assertNotNull(transcodeId);
        assertEquals(Resolution.R_1080P, response.getString("resolution"));
        assertNotNull(response.getString("m3u8Key"));
        assertNotNull(response.getString("outputDir"));
        assertNotNull(response.getJSONObject("uploadCredentials"));
        assertEquals(transcodeId + "-%05d.ts", response.getString("tsFilenameTemplate"));

        Transcode transcode = mongoTemplate.findById(transcodeId, Transcode.class);
        assertNotNull(transcode);
        assertEquals(TranscodeProvider.LOCAL, transcode.getProvider());
        assertEquals(VideoStatus.TRANSCODING, transcode.getStatus());

        Video updated = mongoTemplate.findById(video.getId(), Video.class);
        assertEquals(VideoStatus.TRANSCODING, updated.getStatus());
        assertTrue(updated.getTranscodeIds().contains(transcodeId));
        assertEquals(60_000L, updated.getMediaInfo().getDuration());
        assertEquals(1920, updated.getMediaInfo().getWidth());
        assertEquals("h264", updated.getMediaInfo().getVideoCodec());
    }

    @Test
    void createTranscode_rejectsNonLocalVideo() {
        Video video = createLocalVideo();
        video.setTranscodeMode(TranscodeMode.AUTO);
        mongoTemplate.save(video);

        assertThrows(VideoException.class,
                () -> localTranscodeService.createTranscode(request(video.getId(), Resolution.R_720P)));
    }

    @Test
    void fullFlow_singleRendition_videoReady_withoutAnyCloudTranscode() {
        Video video = createLocalVideo();

        JSONObject response = localTranscodeService.createTranscode(request(video.getId(), Resolution.R_1080P));
        String transcodeId = response.getString("transcodeId");
        String m3u8Key = response.getString("m3u8Key");

        setupOssMocksForFinish(m3u8Key, transcodeId);
        try (MockedStatic<HttpUtil> httpUtil = mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.get(MOCK_PRESIGNED_URL)).thenReturn(m3u8Content(transcodeId));
            localTranscodeService.finishTranscode(transcodeId);
        }

        Transcode transcode = mongoTemplate.findById(transcodeId, Transcode.class);
        assertEquals(TranscodeStatus.FINISHED, transcode.getStatus());
        assertNotNull(transcode.getFinishTime());
        assertEquals(3, transcode.getTsFileIds().size());

        Video updated = mongoTemplate.findById(video.getId(), Video.class);
        assertEquals(VideoStatus.READY, updated.getStatus());
        assertEquals(3, mongoTemplate.findAll(TsFile.class).size());
        verify(videoReadyService).onVideoReady(video.getId());

        // The whole point of local transcode: no paid cloud transcoding is ever called
        verifyNoInteractions(aliyunMpsService);
        verifyNoInteractions(cloudFunctionTranscodeService);
    }

    @Test
    void twoRenditions_videoReadyOnlyAfterBothFinish() {
        Video video = createLocalVideo();

        JSONObject r720 = localTranscodeService.createTranscode(request(video.getId(), Resolution.R_720P));
        JSONObject r1080 = localTranscodeService.createTranscode(request(video.getId(), Resolution.R_1080P));
        String id720 = r720.getString("transcodeId");
        String id1080 = r1080.getString("transcodeId");

        // Finish only the 720p rendition first
        setupOssMocksForFinish(r720.getString("m3u8Key"), id720);
        try (MockedStatic<HttpUtil> httpUtil = mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.get(MOCK_PRESIGNED_URL)).thenReturn(m3u8Content(id720));
            localTranscodeService.finishTranscode(id720);
        }
        assertEquals(VideoStatus.TRANSCODING_PARTLY_COMPLETE,
                mongoTemplate.findById(video.getId(), Video.class).getStatus());
        verify(videoReadyService, never()).onVideoReady(anyString());

        // Now finish the 1080p rendition
        setupOssMocksForFinish(r1080.getString("m3u8Key"), id1080);
        try (MockedStatic<HttpUtil> httpUtil = mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.get(MOCK_PRESIGNED_URL)).thenReturn(m3u8Content(id1080));
            localTranscodeService.finishTranscode(id1080);
        }
        assertEquals(VideoStatus.READY,
                mongoTemplate.findById(video.getId(), Video.class).getStatus());
        verify(videoReadyService).onVideoReady(video.getId());
        verifyNoInteractions(aliyunMpsService);
    }
}
