package com.github.makewheels.video2022.transcode;

import cn.hutool.http.HttpUtil;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.StorageClass;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.transcode.aliyun.AliyunTranscodeStatus;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.contants.Resolution;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import com.github.makewheels.video2022.video.service.VideoReadyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TranscodeCallbackServiceTest extends BaseIntegrationTest {

    @Autowired
    private TranscodeCallbackService transcodeCallbackService;

    // Mock to isolate from downstream side-effects (Ding notifications, storage class changes)
    @MockitoBean
    private VideoReadyService videoReadyService;

    private static final String M3U8_CONTENT = String.join("\n",
            "#EXTM3U",
            "#EXT-X-VERSION:3",
            "#EXT-X-TARGETDURATION:10",
            "#EXTINF:9.009,",
            "segment_0000.ts",
            "#EXTINF:9.009,",
            "segment_0001.ts",
            "#EXTINF:3.003,",
            "segment_0002.ts",
            "#EXT-X-ENDLIST"
    );

    private static final String MOCK_PRESIGNED_URL = "http://mock-oss.local/m3u8?token=xxx";

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    // ──────────────────── Helpers ────────────────────

    private Video createTestVideo(String id, String status, List<String> transcodeIds) {
        Video video = new Video();
        video.setId(id);
        video.setStatus(status);
        video.setUploaderId("u_test");
        video.setVideoType(VideoType.USER_UPLOAD);
        video.setTranscodeIds(transcodeIds);
        video.getMediaInfo().setDuration(21021L); // ~21 seconds in milliseconds
        return video;
    }

    private Transcode createTestTranscode(String id, String videoId, String status,
                                          String resolution, String m3u8Key) {
        Transcode transcode = new Transcode();
        transcode.setId(id);
        transcode.setVideoId(videoId);
        transcode.setProvider(TranscodeProvider.ALIYUN_MPS);
        transcode.setStatus(status);
        transcode.setResolution(resolution);
        transcode.setM3u8Key(m3u8Key);
        return transcode;
    }

    private OSSObject createMockOssObject(String key, long size) {
        ObjectMetadata metadata = mock(ObjectMetadata.class);
        when(metadata.getETag()).thenReturn("mock-etag");
        when(metadata.getContentLength()).thenReturn(size);
        when(metadata.getObjectStorageClass()).thenReturn(StorageClass.Standard);
        when(metadata.getLastModified()).thenReturn(new Date());

        OSSObject ossObject = mock(OSSObject.class);
        when(ossObject.getKey()).thenReturn(key);
        when(ossObject.getObjectMetadata()).thenReturn(metadata);
        return ossObject;
    }

    private List<OSSObjectSummary> createTsSummaries(String prefix, int count) {
        List<OSSObjectSummary> summaries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            OSSObjectSummary summary = new OSSObjectSummary();
            summary.setKey(prefix + String.format("segment_%04d.ts", i));
            summary.setETag("etag-" + i);
            summary.setSize(100_000L + i * 1000L);
            summary.setStorageClass("Standard");
            summary.setLastModified(new Date());
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * Wire up the OssVideoService mocks that TranscodeCallbackService needs
     * (via FileService) to fetch m3u8 metadata, presigned URL, and ts file listings.
     */
    private void setupOssMocks(String m3u8Key, String transcodeFolder) {
        // Create mock OSSObject BEFORE the when() call to avoid nested stubbing
        OSSObject ossObject = createMockOssObject(m3u8Key, 500L);
        when(ossVideoService.getObject(m3u8Key)).thenReturn(ossObject);
        when(ossVideoService.generatePresignedUrl(eq(m3u8Key), any(Duration.class)))
                .thenReturn(MOCK_PRESIGNED_URL);
        when(ossVideoService.listAllObjects(transcodeFolder))
                .thenReturn(createTsSummaries(transcodeFolder, 3));
    }

    /**
     * Run {@code onTranscodeFinish} inside a MockedStatic block for {@link HttpUtil}
     * so the static {@code HttpUtil.get()} call returns our sample m3u8 content.
     */
    private void executeOnTranscodeFinish(Transcode transcode) {
        try (MockedStatic<HttpUtil> httpUtil = mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.get(MOCK_PRESIGNED_URL)).thenReturn(M3U8_CONTENT);
            transcodeCallbackService.onTranscodeFinish(transcode);
        }
    }

    // ──────────────────── Status transition tests ────────────────────

    @Test
    void singleTranscodeComplete_videoBecomesReady() {
        String videoId = "v_single";
        String transcodeId = "t_single";
        String m3u8Key = "video/v_single/transcode/480p/output.m3u8";

        Video video = createTestVideo(videoId, VideoStatus.TRANSCODING, List.of(transcodeId));
        Transcode transcode = createTestTranscode(
                transcodeId, videoId, AliyunTranscodeStatus.TranscodeSuccess,
                Resolution.R_480P, m3u8Key);

        mongoTemplate.save(video);
        mongoTemplate.save(transcode);
        setupOssMocks(m3u8Key, "video/v_single/transcode/480p/");

        executeOnTranscodeFinish(transcode);

        Video updated = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(updated);
        assertEquals(VideoStatus.READY, updated.getStatus());

        verify(videoReadyService).onVideoReady(videoId);
    }

    @Test
    void firstOfMultipleTranscodesComplete_videoBecomesPartlyComplete() {
        String videoId = "v_multi";
        String t480Id = "t_480p";
        String t720Id = "t_720p";
        String m3u8Key480 = "video/v_multi/transcode/480p/output.m3u8";

        Video video = createTestVideo(videoId, VideoStatus.TRANSCODING, List.of(t480Id, t720Id));
        Transcode t480 = createTestTranscode(
                t480Id, videoId, AliyunTranscodeStatus.TranscodeSuccess,
                Resolution.R_480P, m3u8Key480);
        // t720 is still transcoding – NOT finished
        Transcode t720 = createTestTranscode(
                t720Id, videoId, AliyunTranscodeStatus.Transcoding,
                Resolution.R_720P, "video/v_multi/transcode/720p/output.m3u8");

        mongoTemplate.save(video);
        mongoTemplate.save(t480);
        mongoTemplate.save(t720);
        setupOssMocks(m3u8Key480, "video/v_multi/transcode/480p/");

        executeOnTranscodeFinish(t480);

        Video updated = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(updated);
        assertEquals(VideoStatus.TRANSCODING_PARTLY_COMPLETE, updated.getStatus());

        verify(videoReadyService, never()).onVideoReady(anyString());
    }

    @Test
    void allTranscodesComplete_videoBecomesReady() {
        String videoId = "v_all";
        String t480Id = "t_all_480p";
        String t720Id = "t_all_720p";
        String m3u8Key720 = "video/v_all/transcode/720p/output.m3u8";

        Video video = createTestVideo(
                videoId, VideoStatus.TRANSCODING_PARTLY_COMPLETE, List.of(t480Id, t720Id));
        // 480p already finished earlier
        Transcode t480 = createTestTranscode(
                t480Id, videoId, AliyunTranscodeStatus.TranscodeSuccess,
                Resolution.R_480P, "video/v_all/transcode/480p/output.m3u8");
        // 720p just finished now
        Transcode t720 = createTestTranscode(
                t720Id, videoId, AliyunTranscodeStatus.TranscodeSuccess,
                Resolution.R_720P, m3u8Key720);

        mongoTemplate.save(video);
        mongoTemplate.save(t480);
        mongoTemplate.save(t720);
        setupOssMocks(m3u8Key720, "video/v_all/transcode/720p/");

        executeOnTranscodeFinish(t720);

        Video updated = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(updated);
        assertEquals(VideoStatus.READY, updated.getStatus());

        verify(videoReadyService).onVideoReady(videoId);
    }

    // ──────────────────── M3U8 parsing tests ────────────────────

    @Test
    void onTranscodeFinish_m3u8ContentSavedOnTranscode() {
        String videoId = "v_m3u8";
        String transcodeId = "t_m3u8";
        String m3u8Key = "video/v_m3u8/transcode/480p/output.m3u8";

        Video video = createTestVideo(videoId, VideoStatus.TRANSCODING, List.of(transcodeId));
        Transcode transcode = createTestTranscode(
                transcodeId, videoId, AliyunTranscodeStatus.TranscodeSuccess,
                Resolution.R_480P, m3u8Key);

        mongoTemplate.save(video);
        mongoTemplate.save(transcode);
        setupOssMocks(m3u8Key, "video/v_m3u8/transcode/480p/");

        executeOnTranscodeFinish(transcode);

        Transcode updated = mongoTemplate.findById(transcodeId, Transcode.class);
        assertNotNull(updated);
        assertEquals(M3U8_CONTENT, updated.getM3u8Content(),
                "m3u8 content should be persisted on the Transcode document");
    }

    // ──────────────────── TsFile creation tests ────────────────────

    @Test
    void onTranscodeFinish_tsFilesCreatedForEachSegment() {
        String videoId = "v_ts";
        String transcodeId = "t_ts";
        String m3u8Key = "video/v_ts/transcode/480p/output.m3u8";

        Video video = createTestVideo(videoId, VideoStatus.TRANSCODING, List.of(transcodeId));
        Transcode transcode = createTestTranscode(
                transcodeId, videoId, AliyunTranscodeStatus.TranscodeSuccess,
                Resolution.R_480P, m3u8Key);

        mongoTemplate.save(video);
        mongoTemplate.save(transcode);
        setupOssMocks(m3u8Key, "video/v_ts/transcode/480p/");

        executeOnTranscodeFinish(transcode);

        List<TsFile> tsFiles = mongoTemplate.findAll(TsFile.class);
        assertEquals(3, tsFiles.size(), "One TsFile per segment in the m3u8");

        tsFiles.sort(Comparator.comparing(TsFile::getTsIndex));
        for (int i = 0; i < tsFiles.size(); i++) {
            TsFile ts = tsFiles.get(i);
            assertAll("TsFile[" + i + "]",
                    () -> assertNotNull(ts.getId()),
                    () -> assertTrue(ts.getId().startsWith("f_ts_")),
                    () -> assertEquals(videoId, ts.getVideoId()),
                    () -> assertEquals(transcodeId, ts.getTranscodeId()),
                    () -> assertEquals(Resolution.R_480P, ts.getResolution()),
                    () -> assertEquals(FileStatus.READY, ts.getFileStatus()),
                    () -> assertEquals(FileType.TRANSCODE_TS, ts.getFileType()),
                    () -> assertEquals("u_test", ts.getUploaderId())
            );
            assertEquals(i, ts.getTsIndex(), "Segments should be indexed sequentially");
        }
    }

    @Test
    void onTranscodeFinish_tsFileIdsStoredOnTranscode() {
        String videoId = "v_tsids";
        String transcodeId = "t_tsids";
        String m3u8Key = "video/v_tsids/transcode/480p/output.m3u8";

        Video video = createTestVideo(videoId, VideoStatus.TRANSCODING, List.of(transcodeId));
        Transcode transcode = createTestTranscode(
                transcodeId, videoId, AliyunTranscodeStatus.TranscodeSuccess,
                Resolution.R_480P, m3u8Key);

        mongoTemplate.save(video);
        mongoTemplate.save(transcode);
        setupOssMocks(m3u8Key, "video/v_tsids/transcode/480p/");

        executeOnTranscodeFinish(transcode);

        Transcode updated = mongoTemplate.findById(transcodeId, Transcode.class);
        assertNotNull(updated);
        assertNotNull(updated.getTsFileIds());
        assertEquals(3, updated.getTsFileIds().size());

        // Every tsFileId should reference a real TsFile in the database
        for (String tsFileId : updated.getTsFileIds()) {
            TsFile ts = mongoTemplate.findById(tsFileId, TsFile.class);
            assertNotNull(ts, "TsFile " + tsFileId + " should exist in MongoDB");
        }
    }

    // ──────────────────── Bitrate calculation tests ────────────────────

    @Test
    void onTranscodeFinish_bitrateCalculatedOnTranscode() {
        String videoId = "v_bitrate";
        String transcodeId = "t_bitrate";
        String m3u8Key = "video/v_bitrate/transcode/480p/output.m3u8";

        Video video = createTestVideo(videoId, VideoStatus.TRANSCODING, List.of(transcodeId));
        Transcode transcode = createTestTranscode(
                transcodeId, videoId, AliyunTranscodeStatus.TranscodeSuccess,
                Resolution.R_480P, m3u8Key);

        mongoTemplate.save(video);
        mongoTemplate.save(transcode);
        setupOssMocks(m3u8Key, "video/v_bitrate/transcode/480p/");

        executeOnTranscodeFinish(transcode);

        Transcode updated = mongoTemplate.findById(transcodeId, Transcode.class);
        assertNotNull(updated);
        assertNotNull(updated.getAverageBitrate(), "Average bitrate should be calculated");
        assertTrue(updated.getAverageBitrate() > 0);
        assertNotNull(updated.getMaxBitrate(), "Max bitrate should be calculated");
        assertTrue(updated.getMaxBitrate() > 0);
        assertTrue(updated.getMaxBitrate() >= updated.getAverageBitrate(),
                "Max bitrate should be >= average bitrate");
    }

    @Test
    void onTranscodeFinish_eachTsFileHasBitrate() {
        String videoId = "v_tsbr";
        String transcodeId = "t_tsbr";
        String m3u8Key = "video/v_tsbr/transcode/480p/output.m3u8";

        Video video = createTestVideo(videoId, VideoStatus.TRANSCODING, List.of(transcodeId));
        Transcode transcode = createTestTranscode(
                transcodeId, videoId, AliyunTranscodeStatus.TranscodeSuccess,
                Resolution.R_480P, m3u8Key);

        mongoTemplate.save(video);
        mongoTemplate.save(transcode);
        setupOssMocks(m3u8Key, "video/v_tsbr/transcode/480p/");

        executeOnTranscodeFinish(transcode);

        List<TsFile> tsFiles = mongoTemplate.findAll(TsFile.class);
        for (TsFile ts : tsFiles) {
            assertNotNull(ts.getBitrate(), "Every ts segment should have a bitrate");
            assertTrue(ts.getBitrate() > 0);
        }
    }

    // ──────────────────── Edge-case tests ────────────────────

    @Test
    void videoNotFound_returnsGracefully() {
        Transcode transcode = createTestTranscode(
                "t_orphan", "v_nonexistent", AliyunTranscodeStatus.TranscodeSuccess,
                Resolution.R_480P, "video/v_nonexistent/transcode/480p/output.m3u8");
        mongoTemplate.save(transcode);

        // No Video document exists → should return early without error
        assertDoesNotThrow(() -> transcodeCallbackService.onTranscodeFinish(transcode));

        assertEquals(0, mongoTemplate.findAll(TsFile.class).size(),
                "No TsFiles should be created when the video does not exist");
        verify(videoReadyService, never()).onVideoReady(anyString());
    }
}
