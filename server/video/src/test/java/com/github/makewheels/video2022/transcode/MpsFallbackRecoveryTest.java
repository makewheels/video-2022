package com.github.makewheels.video2022.transcode;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.models.QueryJobListResponse;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.contants.TranscodeStatus;
import com.github.makewheels.video2022.transcode.mps.MpsFallbackService;
import com.github.makewheels.video2022.transcode.task.FcTask;
import com.github.makewheels.video2022.transcode.task.FcTaskRepository;
import com.github.makewheels.video2022.transcode.task.FcTaskStatus;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import com.github.makewheels.video2022.video.service.VideoReadyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = {"aliyun.mps.fallback-template.720p=fallback-template"})
class MpsFallbackRecoveryTest extends BaseIntegrationTest {
    @Autowired private MpsFallbackService fallbackService;
    @Autowired private FcTaskRepository taskRepository;
    @MockitoBean private FileService fileService;
    @MockitoBean private VideoReadyService videoReadyService;

    @BeforeEach void setup() { cleanDatabase(); }
    @AfterEach void cleanup() { cleanDatabase(); }

    private FcTask createFallback() {
        Video video = new Video();
        video.setId("v_mps");
        video.setVideoType(VideoType.USER_UPLOAD);
        video.setUploaderId("u_mps");
        video.setStatus(VideoStatus.TRANSCODING);
        video.setTranscodeIds(List.of("tr_mps"));
        video.getMediaInfo().setDuration(6000L);
        video.getMediaInfo().setDynamicRange("SDR");
        video.getMediaInfo().setFrameRate("30/1");
        video.getMediaInfo().setAudioTrackCount(1);
        video.getMediaInfo().setAudioTracks(JSONObject.parseObject("{\"tracks\":[{\"channels\":1}]}"));
        mongoTemplate.save(video);
        Transcode lane = new Transcode();
        lane.setId("tr_mps");
        lane.setVideoId(video.getId());
        lane.setProvider(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU);
        lane.setTaskId("fc_mps");
        lane.setResolution("720p");
        lane.setSourceKey("videos/u_mps/v_mps/raw/source.mp4");
        lane.setM3u8Key("videos/u_mps/v_mps/transcode/tr_mps/attempts/a2/tr_mps.m3u8");
        mongoTemplate.save(lane);
        FcTask task = new FcTask();
        task.setId("fc_mps");
        task.setVideoId(video.getId());
        task.setRefId(lane.getId());
        task.setOperation("TRANSCODE");
        task.setAttemptId("a3");
        task.setStatus(FcTaskStatus.RETRY_WAIT);
        task.setPayloadSnapshot(JSONObject.parseObject("{\"mediaPolicy\":{\"outputWidth\":1280,"
                + "\"outputHeight\":720,\"targetBitrateBps\":4000000}}"));
        taskRepository.save(task);
        when(aliyunMpsService.submitFallbackTranscodeJob(anyString(), anyString(), anyString(), any()))
                .thenReturn("mps_job");
        assertTrue(fallbackService.canFallback(video.getId(), lane.getId()));
        assertTrue(fallbackService.fallback(task));
        return taskRepository.getById(task.getId());
    }

    private void jobState(String state) {
        String json = "{\"body\":{\"jobList\":{\"job\":[{\"jobId\":\"mps_job\",\"state\":\""
                + state + "\",\"output\":{\"properties\":{\"width\":\"1280\",\"height\":\"720\","
                + "\"fps\":\"30\",\"streams\":{\"audioStreamList\":{\"audioStream\":[{\"codecName\":\"aac\",\"channels\":1}]}}}}}]}}}";
        when(aliyunMpsService.queryTranscodeJob("mps_job"))
                .thenReturn(JSONObject.parseObject(json, QueryJobListResponse.class));
    }

    private OSSObject object(String key, byte[] bytes) {
        ObjectMetadata metadata = mock(ObjectMetadata.class);
        when(metadata.getContentLength()).thenReturn((long) bytes.length);
        when(metadata.getETag()).thenReturn("etag");
        when(metadata.getLastModified()).thenReturn(new Date());
        when(metadata.getObjectStorageClass()).thenReturn(com.aliyun.oss.model.StorageClass.Standard);
        OSSObject object = mock(OSSObject.class);
        when(object.getKey()).thenReturn(key);
        when(object.getObjectMetadata()).thenReturn(metadata);
        when(object.getObjectContent()).thenReturn(new ByteArrayInputStream(bytes));
        return object;
    }

    @Test void failedFallback_becomesTerminal_withoutScanningMissingOutput() {
        FcTask task = createFallback();
        jobState("TranscodeFail");
        fallbackService.pollFallbacks();
        assertEquals(FcTaskStatus.FAILED, taskRepository.getById(task.getId()).getStatus());
        assertEquals(TranscodeStatus.FAILED, mongoTemplate.findById("tr_mps", Transcode.class).getStatus());
        assertEquals(VideoStatus.TRANSCODE_FAILED, mongoTemplate.findById("v_mps", Video.class).getStatus());
        verifyNoInteractions(fileService);
        verify(aliyunMpsService, times(1)).submitFallbackTranscodeJob(anyString(), anyString(), anyString(), any());
    }

    @Test void restartScan_registersSuccessfulFallback_beforeReady() {
        FcTask task = createFallback();
        jobState("TranscodeSuccess");
        Transcode lane = mongoTemplate.findById("tr_mps", Transcode.class);
        String key = lane.getM3u8Key();
        String folder = key.substring(0, key.lastIndexOf('/') + 1);
        String playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nseg.ts\n#EXT-X-ENDLIST\n";
        when(fileService.getObject(key)).thenAnswer(inv -> object(key, playlist.getBytes(StandardCharsets.UTF_8)));
        when(fileService.getObject(folder + "seg.ts"))
                .thenAnswer(inv -> object(folder + "seg.ts", new byte[1000]));
        when(fileService.generatePresignedUrl(eq(key), any(Duration.class))).thenReturn("https://mock/playlist");
        OSSObjectSummary segment = new OSSObjectSummary();
        segment.setKey(folder + "seg.ts");
        segment.setSize(1000L);
        segment.setLastModified(new Date());
        segment.setStorageClass("Standard");
        when(fileService.findObjects(folder)).thenReturn(List.of(segment));
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get("https://mock/playlist")).thenReturn(playlist);
            fallbackService.pollFallbacks();
            fallbackService.pollFallbacks();
        }
        assertEquals(FcTaskStatus.SUCCEEDED, taskRepository.getById(task.getId()).getStatus());
        Transcode result = mongoTemplate.findById("tr_mps", Transcode.class);
        assertTrue(result.isSuccessStatus());
        assertEquals(1, result.getTsFileIds().size());
        assertEquals(VideoStatus.READY, mongoTemplate.findById("v_mps", Video.class).getStatus());
        verify(videoReadyService, times(1)).onVideoReady("v_mps");
    }

    @Test void fallbackRequest_preservesMono_andUsesActualDimensions() {
        createFallback();
        var overrides = org.mockito.ArgumentCaptor.forClass(JSONObject.class);
        verify(aliyunMpsService).submitFallbackTranscodeJob(anyString(), anyString(), anyString(), overrides.capture());
        assertEquals("1", overrides.getValue().getJSONObject("Audio").getString("Channels"));
        assertEquals("1280", overrides.getValue().getJSONObject("Video").getString("Width"));
        assertFalse(overrides.getValue().getJSONObject("Video").containsKey("Fps"));
        assertFalse(overrides.getValue().getJSONObject("Video").containsKey("Gop"));
    }

    @Test void roundedMpsSummary_usesPreciseStreamFrameRate() {
        FcTask task = createFallback();
        Video video = mongoTemplate.findById("v_mps", Video.class);
        video.getMediaInfo().setFrameRate("60000/1001");
        mongoTemplate.save(video);
        JSONObject properties = JSONObject.parseObject("{\"width\":1280,\"height\":720,\"fps\":59,"
                + "\"streams\":{\"audioStreamList\":{\"audioStream\":[{\"codecName\":\"aac\",\"channels\":1}]},"
                + "\"videoStreamList\":{\"videoStream\":[{\"fps\":\"59.94006\"}]}}}");
        assertDoesNotThrow(() -> org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(fallbackService, "validateMedia", task, properties));
        assertEquals("59.94006", org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(fallbackService, "actualFrameRate", properties));
        properties.getJSONObject("streams").getJSONObject("videoStreamList")
                .getJSONArray("videoStream").getJSONObject(0).put("fps", "30");
        assertThrows(IllegalStateException.class, () -> org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(fallbackService, "validateMedia", task, properties));
    }


    @Test void actualLowerFrameRate_failsWithoutScanningOutput() {
        FcTask task = createFallback();
        Video video = mongoTemplate.findById("v_mps", Video.class);
        video.getMediaInfo().setFrameRate("60000/1001");
        mongoTemplate.save(video);
        jobState("TranscodeSuccess");
        fallbackService.pollFallbacks();
        assertEquals(FcTaskStatus.FAILED, taskRepository.getById(task.getId()).getStatus());
        assertEquals(VideoStatus.TRANSCODE_FAILED, mongoTemplate.findById("v_mps", Video.class).getStatus());
        verifyNoInteractions(fileService);
    }


    @Test void changedAudioChannels_rejectedBeforeRegistration() {
        FcTask task = createFallback();
        JSONObject properties = JSONObject.parseObject("{\"width\":1280,\"height\":720,\"fps\":30,"
                + "\"streams\":{\"audioStreamList\":{\"audioStream\":[{\"codecName\":\"aac\",\"channels\":2}]}}}");
        assertThrows(IllegalStateException.class, () -> org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(fallbackService, "validateMedia", task, properties));
    }

}
