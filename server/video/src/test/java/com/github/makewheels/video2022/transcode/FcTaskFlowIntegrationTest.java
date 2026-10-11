package com.github.makewheels.video2022.transcode;

import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.StorageClass;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.file.TsFileRepository;
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionClient;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.contants.TranscodeStatus;
import com.github.makewheels.video2022.transcode.task.FcTask;
import com.github.makewheels.video2022.transcode.task.FcTaskCallbackService;
import com.github.makewheels.video2022.transcode.task.FcTaskRepository;
import com.github.makewheels.video2022.transcode.task.FcTaskStatus;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import com.github.makewheels.video2022.video.service.VideoReadyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 自建链路任务流集成测试：probe 回调选档建档（T06）、回调身份校验（T08）、
 * 重复/晚到回调幂等（T09）、产物校验失败不就绪（T13）、重试耗尽 MPS 兜底至多一次（T11）。
 */
@TestPropertySource(properties = {
        "aliyun.mps.fallback-template.720p=tpl-fallback-720p",
        "aliyun.mps.fallback-template.1080p=tpl-fallback-1080p"
})
class FcTaskFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private FcTaskCallbackService fcTaskCallbackService;
    @Autowired
    private FcTaskRepository fcTaskRepository;
    @Autowired
    private TsFileRepository tsFileRepository;
    @Autowired
    private TranscodeLauncher transcodeLauncher;
    @Autowired
    private com.github.makewheels.video2022.transcode.task.FcTimeoutRecoveryService fcTimeoutRecoveryService;

    @MockitoBean
    private CloudFunctionClient cloudFunctionClient;
    @MockitoBean
    private VideoReadyService videoReadyService;
    @MockitoBean
    private com.github.makewheels.video2022.file.FileService fileService;

    private static final String MOCK_PRESIGNED_URL = "http://mock-oss.local/m3u8?token=xxx";

    @BeforeEach
    void setUp() {
        cleanDatabase();
        when(cloudFunctionClient.isConfigured(anyString())).thenReturn(true);
        CloudFunctionClient.SubmitResult accepted = new CloudFunctionClient.SubmitResult();
        accepted.setAccepted(true);
        accepted.setRequestId("fc-req-1");
        accepted.setHttpStatus(202);
        when(cloudFunctionClient.submit(anyString(), any(JSONObject.class))).thenReturn(accepted);
        when(cloudFunctionClient.buildPayload(any(), any(), any(), any(), any()))
                .thenCallRealMethod();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    // ──────────────────── helpers ────────────────────

    private Video createVideo(String id, String status) {
        Video video = new Video();
        video.setId(id);
        video.setStatus(status);
        video.setUploaderId("u_test");
        video.setVideoType(VideoType.USER_UPLOAD);
        video.setCreateTime(new Date());
        video.getMediaInfo().setDuration(21000L);
        return video;
    }

    private JSONObject probeManifest(int displayWidth, int displayHeight) {
        JSONObject media = new JSONObject();
        media.put("width", displayWidth);
        media.put("height", displayHeight);
        media.put("displayWidth", displayWidth);
        media.put("displayHeight", displayHeight);
        media.put("durationMs", 21000L);
        media.put("videoCodec", "h264");
        media.put("audioCodec", "aac");
        media.put("hasAudio", true);
        media.put("bitrateBps", 5_000_000L);
        media.put("frameRate", "30000/1001");
        media.put("frameRateMode", "CFR");
        media.put("sar", "1:1");
        media.put("rotation", 0);
        media.put("pixFmt", "yuv420p");
        media.put("bitDepth", 8);
        media.put("dynamicRange", "SDR");
        media.put("audioTrackCount", 1);
        JSONObject manifest = new JSONObject();
        manifest.put("schemaVersion", 1);
        manifest.put("taskId", "task_probe");
        manifest.put("attemptId", "a_probe");
        manifest.put("operation", "PROBE");
        manifest.put("mediaInfo", media);
        return manifest;
    }

    private OSSObject ossObjectWithContent(String key, String content) {
        // 先完成内部 mock 的构建，再交给外层 when()，避免嵌套 stubbing
        ObjectMetadata metadata = mock(ObjectMetadata.class);
        when(metadata.getETag()).thenReturn("mock-etag");
        when(metadata.getContentLength())
                .thenReturn((long) content.getBytes(StandardCharsets.UTF_8).length);
        when(metadata.getObjectStorageClass()).thenReturn(com.aliyun.oss.model.StorageClass.Standard);
        when(metadata.getLastModified()).thenReturn(new Date());
        OSSObject object = mock(OSSObject.class);
        when(object.getKey()).thenReturn(key);
        when(object.getObjectMetadata()).thenReturn(metadata);
        when(object.getObjectContent()).thenReturn(new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8)));
        return object;
    }

    private String tsPlaylist(String segmentPrefix, int count) {
        StringBuilder sb = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n");
        for (int i = 0; i < count; i++) {
            sb.append("#EXTINF:9.009,\n").append(segmentPrefix).append(i).append(".ts\n");
        }
        sb.append("#EXT-X-ENDLIST\n");
        return sb.toString();
    }

    private List<OSSObjectSummary> summaries(String prefix, int count) {
        List<OSSObjectSummary> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            OSSObjectSummary s = new OSSObjectSummary();
            s.setKey(prefix + "seg_" + i + ".ts");
            s.setSize(100_000L + i * 1000L);
            s.setETag("etag-" + i);
            s.setStorageClass("Standard");
            s.setLastModified(new Date());
            list.add(s);
        }
        return list;
    }

    /**
     * 完整 probe → 选档建档流程，返回落库的 transcode 列表。
     */
    private List<Transcode> runProbeFlow(String videoId, int displayWidth, int displayHeight) {
        Video video = createVideo(videoId, VideoStatus.TRANSCODING);
        video.setRawFileId("raw_1");
        mongoTemplate.save(video);
        when(fileService.getKeyByFileId("raw_1"))
                .thenReturn("videos/u_test/" + video.getId() + "/raw/raw_1.mp4");

        // 1. 发起探测
        transcodeLauncher.startProbe(video);
        FcTask probeTask = fcTaskRepository.getById(
                mongoTemplate.findAll(FcTask.class).get(0).getId());

        // 2. mock 探测产物 manifest
        JSONObject manifest = probeManifest(displayWidth, displayHeight);
        manifest.put("taskId", probeTask.getId());
        manifest.put("attemptId", probeTask.getAttemptId());
        String manifestKey = "videos/u_test/" + videoId + "/probe/" + probeTask.getId() + "/result.json";
        OSSObject manifestObject = ossObjectWithContent(manifestKey, manifest.toJSONString());
        when(fileService.getObject(manifestKey)).thenReturn(manifestObject);

        // 3. 回调 SUCCEEDED
        JSONObject body = new JSONObject();
        body.put("taskId", probeTask.getId());
        body.put("attemptId", probeTask.getAttemptId());
        body.put("operation", "PROBE");
        body.put("status", "SUCCEEDED");
        body.put("outputManifestKey", manifestKey);
        int code = fcTaskCallbackService.handleCallback(body);
        assertEquals(200, code);

        return mongoTemplate.find(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("videoId").is(videoId)),
                Transcode.class);
    }

    // ──────────────────── T06 ────────────────────

    @Test
    void probeCallback_createsAllLanesBeforeAnyCompletion() {
        List<Transcode> transcodes = runProbeFlow("v_t06", 1920, 1080);

        // 1080p SDR 源 → 两档：720p GPU（缩放）、1080p CPU remux
        assertEquals(2, transcodes.size());
        long gpuCount = transcodes.stream()
                .filter(t -> TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU.equals(t.getProvider())).count();
        long cpuCount = transcodes.stream()
                .filter(t -> TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU.equals(t.getProvider())).count();
        assertEquals(1, gpuCount);
        assertEquals(1, cpuCount);

        // 全部档位任务身份已持久化且已提交
        List<FcTask> tasks = mongoTemplate.findAll(FcTask.class).stream()
                .filter(t -> "TRANSCODE".equals(t.getOperation())).toList();
        assertEquals(2, tasks.size());
        assertTrue(tasks.stream().allMatch(t -> FcTaskStatus.SUBMITTED.equals(t.getStatus())));
        assertTrue(tasks.stream().allMatch(t -> t.getPayloadSnapshot() != null));

        // 提交协议里没有 480p
        assertTrue(transcodes.stream().noneMatch(t -> "480p".equals(t.getResolution())));

        Video video = mongoTemplate.findById("v_t06", Video.class);
        assertEquals(VideoStatus.TRANSCODING, video.getStatus());
    }

    // ──────────────────── T06/T09 首档回调 + 幂等 ────────────────────

    @Test
    void firstLaneCallback_partlyComplete_thenDuplicateIgnored() {
        List<Transcode> transcodes = runProbeFlow("v_t09", 1920, 1080);
        Transcode gpuLane = transcodes.stream()
                .filter(t -> TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU.equals(t.getProvider()))
                .findFirst().orElseThrow();

        String folder = gpuLane.getM3u8Key().substring(0, gpuLane.getM3u8Key().lastIndexOf('/') + 1);
        String playlist = tsPlaylist("seg_", 2);
        when(fileService.generatePresignedUrl(eq(gpuLane.getM3u8Key()), any(Duration.class)))
                .thenReturn(MOCK_PRESIGNED_URL);
        OSSObject playlistObject = ossObjectWithContent(gpuLane.getM3u8Key(), playlist);
        when(fileService.getObject(gpuLane.getM3u8Key())).thenReturn(playlistObject);
        when(fileService.findObjects(folder)).thenReturn(summaries(folder, 2));
        // validateOutput 逐分片存在性检查
        for (int i = 0; i < 2; i++) {
            String segKey = folder + "seg_" + i + ".ts";
            OSSObject segObject = ossObjectWithContent(segKey, "segdata");
            when(fileService.getObject(segKey)).thenReturn(segObject);
        }

        FcTask laneTask = mongoTemplate.findById(gpuLane.getTaskId(), FcTask.class);
        JSONObject body = new JSONObject();
        body.put("taskId", laneTask.getId());
        body.put("attemptId", laneTask.getAttemptId());
        body.put("operation", "TRANSCODE");
        body.put("status", "SUCCEEDED");
        JSONObject manifest = new JSONObject();
        manifest.put("playlistKey", gpuLane.getM3u8Key());
        manifest.put("width", 1280);
        manifest.put("height", 720);
        manifest.put("frameRate", "30000/1001");
        manifest.put("dynamicRange", "SDR");
        manifest.put("hlsCodecs", "avc1.640029,mp4a.40.2");
        body.put("outputManifestKey", folder + "result.json");
        OSSObject resultObject = ossObjectWithContent(folder + "result.json", manifest.toJSONString());
        when(fileService.getObject(folder + "result.json")).thenReturn(resultObject);

        try (MockedStatic<HttpUtil> httpUtil = mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.get(MOCK_PRESIGNED_URL)).thenReturn(playlist);
            fcTaskCallbackService.handleCallback(body);

            Video video = mongoTemplate.findById("v_t09", Video.class);
            assertEquals(VideoStatus.TRANSCODING_PARTLY_COMPLETE, video.getStatus());
            Transcode updated = mongoTemplate.findById(gpuLane.getId(), Transcode.class);
            assertEquals(TranscodeStatus.FINISHED, updated.getStatus());
            assertEquals(2, tsFileRepository.getByTranscodeId(gpuLane.getId()).size());
            assertEquals(1280, updated.getActualWidth());

            // 重复成功回调：不重复登记
            fcTaskCallbackService.handleCallback(body);
            assertEquals(2, tsFileRepository.getByTranscodeId(gpuLane.getId()).size());
        }
    }

    // ──────────────────── T08 ────────────────────

    @Test
    void callback_wrongAttemptOrUnknownTask_rejected() {
        List<Transcode> transcodes = runProbeFlow("v_t08", 1920, 1080);
        Transcode lane = transcodes.get(0);
        FcTask task = mongoTemplate.findById(lane.getTaskId(), FcTask.class);

        // 错 attemptId：200 忽略，状态不变
        JSONObject stale = new JSONObject();
        stale.put("taskId", task.getId());
        stale.put("attemptId", "stale_attempt");
        stale.put("status", "FAILED");
        stale.put("errorMessage", "late");
        assertEquals(200, fcTaskCallbackService.handleCallback(stale));
        FcTask unchanged = mongoTemplate.findById(task.getId(), FcTask.class);
        assertEquals(FcTaskStatus.SUBMITTED, unchanged.getStatus());
        assertEquals(1, unchanged.getAttemptCount());

        // 未知 taskId：404
        JSONObject unknown = new JSONObject();
        unknown.put("taskId", "task_nonexistent");
        unknown.put("attemptId", "x");
        unknown.put("status", "SUCCEEDED");
        assertEquals(404, fcTaskCallbackService.handleCallback(unknown));
    }

    // ──────────────────── T13 ────────────────────

    @Test
    void validationMissingEndlist_transcodeFailsNotReady() {
        List<Transcode> transcodes = runProbeFlow("v_t13", 1280, 720);
        Transcode lane = transcodes.get(0);

        String folder = lane.getM3u8Key().substring(0, lane.getM3u8Key().lastIndexOf('/') + 1);
        // 缺 ENDLIST 的 playlist
        String badPlaylist = "#EXTM3U\n#EXTINF:9.009,\nseg_0.ts\n";
        OSSObject badPlaylistObject = ossObjectWithContent(lane.getM3u8Key(), badPlaylist);
        when(fileService.getObject(lane.getM3u8Key())).thenReturn(badPlaylistObject);

        FcTask task = mongoTemplate.findById(lane.getTaskId(), FcTask.class);
        JSONObject body = new JSONObject();
        body.put("taskId", task.getId());
        body.put("attemptId", task.getAttemptId());
        body.put("status", "SUCCEEDED");
        body.put("operation", "TRANSCODE");
        body.put("outputManifestKey", folder + "result.json");
        JSONObject manifest = new JSONObject();
        manifest.put("playlistKey", lane.getM3u8Key());
        OSSObject emptyResultObject = ossObjectWithContent(folder + "result.json", manifest.toJSONString());
        when(fileService.getObject(folder + "result.json")).thenReturn(emptyResultObject);

        assertEquals(200, fcTaskCallbackService.handleCallback(body));

        Transcode updated = mongoTemplate.findById(lane.getId(), Transcode.class);
        assertEquals(TranscodeStatus.FAILED, updated.getStatus());
        assertNotNull(updated.getErrorMessage());
        Video video = mongoTemplate.findById("v_t13", Video.class);
        // 单档全部失败 → TRANSCODE_FAILED，不 READY
        assertEquals(VideoStatus.TRANSCODE_FAILED, video.getStatus());
    }

    // ──────────────────── T11 ────────────────────

    @Test
    void retryExhausted_mpsFallbackAtMostOnce() {
        List<Transcode> transcodes = runProbeFlow("v_t11", 1280, 720);
        Transcode lane = transcodes.get(0);
        when(aliyunMpsService.submitFallbackTranscodeJob(anyString(), anyString(), anyString()))
                .thenReturn("mps-job-1");

        // 第一次失败：还有重试额度（maxAttempts=2）
        FcTask task = mongoTemplate.findById(lane.getTaskId(), FcTask.class);
        JSONObject fail1 = new JSONObject();
        fail1.put("taskId", task.getId());
        fail1.put("attemptId", task.getAttemptId());
        fail1.put("status", "FAILED");
        fail1.put("errorMessage", "nvenc error");
        assertEquals(200, fcTaskCallbackService.handleCallback(fail1));

        FcTask afterFirst = mongoTemplate.findById(task.getId(), FcTask.class);
        assertEquals(FcTaskStatus.RETRY_WAIT, afterFirst.getStatus());
        assertEquals(2, afterFirst.getAttemptCount());

        // 恢复流程重提交（模拟退避到期：把 deadline 拨到过去），任务回到 SUBMITTED
        afterFirst.setDeadline(new Date(System.currentTimeMillis() - 1000));
        fcTaskRepository.save(afterFirst);
        fcTimeoutRecoveryService.recover();
        FcTask resubmitted = mongoTemplate.findById(task.getId(), FcTask.class);
        assertEquals(FcTaskStatus.SUBMITTED, resubmitted.getStatus());

        // 第二次失败（用重提交后的 attemptId）：额度耗尽 → MPS 兜底
        JSONObject fail2 = new JSONObject();
        fail2.put("taskId", task.getId());
        fail2.put("attemptId", resubmitted.getAttemptId());
        fail2.put("status", "FAILED");
        fail2.put("errorMessage", "nvenc error again");
        assertEquals(200, fcTaskCallbackService.handleCallback(fail2));

        Transcode afterFallback = mongoTemplate.findById(lane.getId(), Transcode.class);
        assertEquals(1, afterFallback.getFallbackCount());
        assertEquals(TranscodeProvider.ALIYUN_MPS, afterFallback.getCurrentProvider());
        assertEquals("mps-job-1", afterFallback.getJobId());
        verify(aliyunMpsService, times(1))
                .submitFallbackTranscodeJob(anyString(), anyString(), anyString());

        // 兜底后再来失败回调：不重复兜底
        JSONObject fail3 = new JSONObject();
        fail3.put("taskId", task.getId());
        fail3.put("attemptId", "another-attempt");
        fail3.put("status", "FAILED");
        assertEquals(200, fcTaskCallbackService.handleCallback(fail3));
        verify(aliyunMpsService, times(1))
                .submitFallbackTranscodeJob(anyString(), anyString(), anyString());
    }

    // ──────────────────── T05 ────────────────────

    @Test
    void unknownProvider_neverTreatedAsSuccess() {
        Transcode unknown = new Transcode();
        unknown.setId("t_unknown");
        unknown.setProvider(null);
        unknown.setStatus("Whatever");
        assertFalse(unknown.isFinishStatus());
        assertFalse(unknown.isSuccessStatus());

        Transcode gpu = new Transcode();
        gpu.setProvider(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU);
        gpu.setStatus(VideoStatus.TRANSCODING);
        assertFalse(gpu.isFinishStatus());
        gpu.setStatus(TranscodeStatus.FINISHED);
        assertTrue(gpu.isSuccessStatus());
        gpu.setStatus(TranscodeStatus.FAILED);
        assertTrue(gpu.isFinishStatus());
        assertFalse(gpu.isSuccessStatus());
    }

    // ──────────────────── T07 提交分类 ────────────────────

    @Test
    void submitThrottled_goesRetryWaitWithoutConsumingAttempt() {
        Video video = createVideo("v_t07", VideoStatus.TRANSCODING);
        video.setRawFileId("raw_1");
        mongoTemplate.save(video);
        when(fileService.getKeyByFileId("raw_1")).thenReturn("videos/u_test/v_t07/raw/raw_1.mp4");

        CloudFunctionClient.SubmitResult throttled = new CloudFunctionClient.SubmitResult();
        throttled.setAccepted(false);
        throttled.setThrottled(true);
        throttled.setHttpStatus(429);
        when(cloudFunctionClient.submit(anyString(), any(JSONObject.class))).thenReturn(throttled);

        transcodeLauncher.startProbe(video);
        FcTask task = mongoTemplate.findAll(FcTask.class).get(0);
        assertEquals(FcTaskStatus.RETRY_WAIT, task.getStatus());
        // 未受理不消耗 attempt（保持首次提交时的计数）
        assertEquals(1, task.getAttemptCount());
    }
}
