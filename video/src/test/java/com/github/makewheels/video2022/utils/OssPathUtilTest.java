package com.github.makewheels.video2022.utils;

import com.github.makewheels.video2022.cover.Cover;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.video.bean.entity.Video;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class OssPathUtilTest {

    private Video createVideo(String id, String uploaderId, Date createTime) {
        Video video = new Video();
        video.setId(id);
        video.setUploaderId(uploaderId);
        video.setCreateTime(createTime);
        return video;
    }

    private Cover createCover(String id, String extension) {
        Cover cover = new Cover();
        cover.setId(id);
        cover.setExtension(extension);
        return cover;
    }

    private File createFile(String id, String extension) {
        File file = new File();
        file.setId(id);
        file.setExtension(extension);
        return file;
    }

    private Transcode createTranscode(String id) {
        Transcode transcode = new Transcode();
        transcode.setId(id);
        return transcode;
    }

    private Date makeDate(int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 15, 10, 30, 0);
        return cal.getTime();
    }

    // ── getVideoPrefix ─────────────────────────────────────────────

    @Test
    void getVideoPrefix_typicalDate() {
        Date date = makeDate(2024, 3);
        Video video = createVideo("vid001", "user42", date);

        String prefix = OssPathUtil.getVideoPrefix(video);

        assertEquals("videos/user42/202403/vid001", prefix);
    }

    @Test
    void getVideoPrefix_januaryDate() {
        Date date = makeDate(2023, 1);
        Video video = createVideo("v100", "uploaderABC", date);

        assertEquals("videos/uploaderABC/202301/v100", OssPathUtil.getVideoPrefix(video));
    }

    @Test
    void getVideoPrefix_decemberDate() {
        Date date = makeDate(2025, 12);
        Video video = createVideo("vid-dec", "u1", date);

        assertEquals("videos/u1/202512/vid-dec", OssPathUtil.getVideoPrefix(video));
    }

    @Test
    void getVideoPrefix_dateFormatMatchesYyyyMm() {
        Date date = makeDate(2022, 7);
        Video video = createVideo("abc", "xyz", date);

        String prefix = OssPathUtil.getVideoPrefix(video);
        // Extract the date segment (third path component)
        String[] parts = prefix.split("/");
        assertEquals(4, parts.length, "prefix should have 4 path segments");
        String dateSegment = parts[2];
        // Must be exactly 6 digits: yyyyMM
        assertTrue(dateSegment.matches("\\d{6}"), "date segment should be yyyyMM format: " + dateSegment);
        assertEquals("202207", dateSegment);
    }

    @Test
    void getVideoPrefix_structureIsCorrect() {
        Date date = makeDate(2024, 5);
        Video video = createVideo("myVideoId", "myUploaderId", date);

        String prefix = OssPathUtil.getVideoPrefix(video);

        assertTrue(prefix.startsWith("videos/"));
        assertTrue(prefix.contains("myUploaderId"));
        assertTrue(prefix.endsWith("myVideoId"));

        String[] segments = prefix.split("/");
        assertEquals("videos", segments[0]);
        assertEquals("myUploaderId", segments[1]);
        assertEquals("202405", segments[2]);
        assertEquals("myVideoId", segments[3]);
    }

    // ── getCoverKey ────────────────────────────────────────────────

    @Test
    void getCoverKey_typicalInputs() {
        Date date = makeDate(2024, 6);
        Video video = createVideo("vid01", "user01", date);
        Cover cover = createCover("cover01", "jpg");
        File file = createFile("file01", "jpg");

        String key = OssPathUtil.getCoverKey(video, cover, file);

        assertEquals("videos/user01/202406/vid01/cover/cover01/file01.jpg", key);
    }

    @Test
    void getCoverKey_pngExtension() {
        Date date = makeDate(2023, 11);
        Video video = createVideo("v2", "u2", date);
        Cover cover = createCover("c2", "png");
        File file = createFile("f2", "png");

        String key = OssPathUtil.getCoverKey(video, cover, file);

        assertTrue(key.endsWith(".png"));
        assertEquals("videos/u2/202311/v2/cover/c2/f2.png", key);
    }

    @Test
    void getCoverKey_usesCoversExtensionNotFiles() {
        Date date = makeDate(2024, 1);
        Video video = createVideo("v", "u", date);
        Cover cover = createCover("cov", "webp");
        File file = createFile("fil", "jpg"); // file extension differs from cover

        String key = OssPathUtil.getCoverKey(video, cover, file);

        // The method uses cover.getExtension(), not file.getExtension()
        assertTrue(key.endsWith(".webp"), "should use cover extension");
        assertFalse(key.endsWith(".jpg"));
    }

    @Test
    void getCoverKey_containsCoverSubpath() {
        Date date = makeDate(2024, 2);
        Video video = createVideo("vid", "uid", date);
        Cover cover = createCover("cid", "jpg");
        File file = createFile("fid", "jpg");

        String key = OssPathUtil.getCoverKey(video, cover, file);

        assertTrue(key.contains("/cover/"));
    }

    // ── getRawFileKey ──────────────────────────────────────────────

    @Test
    void getRawFileKey_typicalInputs() {
        Date date = makeDate(2024, 8);
        Video video = createVideo("vid10", "uploader10", date);
        File rawFile = createFile("rawFile10", "mp4");

        String key = OssPathUtil.getRawFileKey(video, rawFile);

        assertEquals("videos/uploader10/202408/vid10/raw/rawFile10/rawFile10.mp4", key);
    }

    @Test
    void getRawFileKey_movExtension() {
        Date date = makeDate(2023, 4);
        Video video = createVideo("vMov", "uMov", date);
        File rawFile = createFile("fMov", "mov");

        String key = OssPathUtil.getRawFileKey(video, rawFile);

        assertTrue(key.endsWith(".mov"));
        assertEquals("videos/uMov/202304/vMov/raw/fMov/fMov.mov", key);
    }

    @Test
    void getRawFileKey_containsRawSubpath() {
        Date date = makeDate(2024, 9);
        Video video = createVideo("v", "u", date);
        File rawFile = createFile("rf", "avi");

        String key = OssPathUtil.getRawFileKey(video, rawFile);

        assertTrue(key.contains("/raw/"));
    }

    @Test
    void getRawFileKey_fileIdAppearsInBothDirAndFilename() {
        Date date = makeDate(2024, 10);
        Video video = createVideo("v1", "u1", date);
        File rawFile = createFile("myRawId", "mp4");

        String key = OssPathUtil.getRawFileKey(video, rawFile);

        // The raw file ID should appear twice: once as directory, once as filename
        assertEquals("videos/u1/202410/v1/raw/myRawId/myRawId.mp4", key);
        // Verify by counting occurrences
        int count = key.split("myRawId", -1).length - 1;
        assertEquals(2, count, "rawFile id should appear twice in the key");
    }

    // ── getTranscodePrefix ─────────────────────────────────────────

    @Test
    void getTranscodePrefix_typicalInput() {
        Date date = makeDate(2024, 5);
        Video video = createVideo("vid20", "user20", date);

        String prefix = OssPathUtil.getTranscodePrefix(video);

        assertEquals("videos/user20/202405/vid20/transcode", prefix);
    }

    @Test
    void getTranscodePrefix_endsWithTranscode() {
        Date date = makeDate(2023, 2);
        Video video = createVideo("v", "u", date);

        String prefix = OssPathUtil.getTranscodePrefix(video);

        assertTrue(prefix.endsWith("/transcode"));
    }

    @Test
    void getTranscodePrefix_startsWithVideoPrefix() {
        Date date = makeDate(2024, 7);
        Video video = createVideo("vt", "ut", date);

        String videoPrefix = OssPathUtil.getVideoPrefix(video);
        String transcodePrefix = OssPathUtil.getTranscodePrefix(video);

        assertTrue(transcodePrefix.startsWith(videoPrefix));
        assertEquals(videoPrefix + "/transcode", transcodePrefix);
    }

    // ── getM3u8Key ─────────────────────────────────────────────────

    @Test
    void getM3u8Key_typicalInput() {
        Date date = makeDate(2024, 4);
        Video video = createVideo("vid30", "user30", date);
        Transcode transcode = createTranscode("trans01");

        String key = OssPathUtil.getM3u8Key(video, transcode);

        assertEquals("videos/user30/202404/vid30/transcode/trans01/trans01.m3u8", key);
    }

    @Test
    void getM3u8Key_endsWithM3u8Extension() {
        Date date = makeDate(2023, 6);
        Video video = createVideo("v", "u", date);
        Transcode transcode = createTranscode("t1");

        String key = OssPathUtil.getM3u8Key(video, transcode);

        assertTrue(key.endsWith(".m3u8"));
    }

    @Test
    void getM3u8Key_transcodeIdAppearsInBothDirAndFilename() {
        Date date = makeDate(2024, 11);
        Video video = createVideo("v1", "u1", date);
        Transcode transcode = createTranscode("myTransId");

        String key = OssPathUtil.getM3u8Key(video, transcode);

        assertEquals("videos/u1/202411/v1/transcode/myTransId/myTransId.m3u8", key);
        int count = key.split("myTransId", -1).length - 1;
        assertEquals(2, count, "transcode id should appear twice in the key");
    }

    @Test
    void getM3u8Key_startsWithTranscodePrefix() {
        Date date = makeDate(2024, 3);
        Video video = createVideo("v2", "u2", date);
        Transcode transcode = createTranscode("t2");

        String transcodePrefix = OssPathUtil.getTranscodePrefix(video);
        String m3u8Key = OssPathUtil.getM3u8Key(video, transcode);

        assertTrue(m3u8Key.startsWith(transcodePrefix));
    }

    // ── Cross-method consistency ───────────────────────────────────

    @Test
    void allKeys_shareCommonVideoPrefix() {
        Date date = makeDate(2024, 9);
        Video video = createVideo("sharedVid", "sharedUser", date);
        Cover cover = createCover("c1", "jpg");
        File coverFile = createFile("cf1", "jpg");
        File rawFile = createFile("rf1", "mp4");
        Transcode transcode = createTranscode("t1");

        String videoPrefix = OssPathUtil.getVideoPrefix(video);
        String coverKey = OssPathUtil.getCoverKey(video, cover, coverFile);
        String rawKey = OssPathUtil.getRawFileKey(video, rawFile);
        String transcodePrefix = OssPathUtil.getTranscodePrefix(video);
        String m3u8Key = OssPathUtil.getM3u8Key(video, transcode);

        // All paths must start with the same video prefix
        assertAll(
                () -> assertTrue(coverKey.startsWith(videoPrefix), "cover key should start with video prefix"),
                () -> assertTrue(rawKey.startsWith(videoPrefix), "raw key should start with video prefix"),
                () -> assertTrue(transcodePrefix.startsWith(videoPrefix), "transcode prefix should start with video prefix"),
                () -> assertTrue(m3u8Key.startsWith(videoPrefix), "m3u8 key should start with video prefix")
        );
    }

    @Test
    void allKeys_noDoubleSlashes() {
        Date date = makeDate(2024, 2);
        Video video = createVideo("v", "u", date);
        Cover cover = createCover("c", "jpg");
        File file = createFile("f", "jpg");
        Transcode transcode = createTranscode("t");

        assertAll(
                () -> assertFalse(OssPathUtil.getVideoPrefix(video).contains("//"), "videoPrefix no double slash"),
                () -> assertFalse(OssPathUtil.getCoverKey(video, cover, file).contains("//"), "coverKey no double slash"),
                () -> assertFalse(OssPathUtil.getRawFileKey(video, file).contains("//"), "rawFileKey no double slash"),
                () -> assertFalse(OssPathUtil.getTranscodePrefix(video).contains("//"), "transcodePrefix no double slash"),
                () -> assertFalse(OssPathUtil.getM3u8Key(video, transcode).contains("//"), "m3u8Key no double slash")
        );
    }

    @Test
    void allKeys_noLeadingSlash() {
        Date date = makeDate(2024, 2);
        Video video = createVideo("v", "u", date);

        assertAll(
                () -> assertFalse(OssPathUtil.getVideoPrefix(video).startsWith("/"), "no leading slash on prefix"),
                () -> assertFalse(OssPathUtil.getTranscodePrefix(video).startsWith("/"), "no leading slash on transcode prefix")
        );
    }
}
