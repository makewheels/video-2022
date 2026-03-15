package com.github.makewheels.video2022.video;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.contants.TranscodeStatus;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.service.LinkService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link LinkService} — MD5-based file deduplication.
 */
class LinkServiceTest extends BaseIntegrationTest {

    @Autowired
    private LinkService linkService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    // ──────────────────── isOriginVideoExist tests ────────────────────

    @Test
    void isOriginVideoExist_noFileWithMd5_returnsFalse() {
        assertFalse(linkService.isOriginVideoExist("nonexistent-md5"));
    }

    @Test
    void isOriginVideoExist_fileExistsButNotOnOSS_returnsFalse() {
        Video video = new Video();
        video.setId("v_oss-miss");
        video.setStatus(VideoStatus.READY);
        mongoTemplate.save(video);

        File file = new File();
        file.setId("f_oss-miss");
        file.setMd5("md5-oss-miss");
        file.setVideoId(video.getId());
        file.setKey("videos/test/raw/missing.mp4");
        file.setDeleted(false);
        mongoTemplate.save(file);

        // OSS object does NOT exist
        when(ossVideoService.doesObjectExist(anyString())).thenReturn(false);

        assertFalse(linkService.isOriginVideoExist("md5-oss-miss"));
    }

    @Test
    void isOriginVideoExist_fileExistsVideoNotReady_returnsFalse() {
        Video video = new Video();
        video.setId("v_not-ready");
        video.setStatus(VideoStatus.TRANSCODING);
        mongoTemplate.save(video);

        File file = new File();
        file.setId("f_not-ready");
        file.setMd5("md5-not-ready");
        file.setVideoId(video.getId());
        file.setKey("videos/test/raw/notready.mp4");
        file.setDeleted(false);
        mongoTemplate.save(file);

        when(ossVideoService.doesObjectExist(anyString())).thenReturn(true);

        assertFalse(linkService.isOriginVideoExist("md5-not-ready"));
    }

    @Test
    void isOriginVideoExist_allConditionsMet_returnsTrue() {
        Transcode transcode = new Transcode();
        transcode.setId("tc_exist");
        transcode.setStatus(TranscodeStatus.FINISHED);
        transcode.setM3u8Key("videos/test/ts/master.m3u8");
        mongoTemplate.save(transcode);

        Video video = new Video();
        video.setId("v_exist");
        video.setStatus(VideoStatus.READY);
        video.setTranscodeIds(List.of(transcode.getId()));
        mongoTemplate.save(video);

        File file = new File();
        file.setId("f_exist");
        file.setMd5("md5-exist");
        file.setVideoId(video.getId());
        file.setKey("videos/test/raw/exist.mp4");
        file.setDeleted(false);
        mongoTemplate.save(file);

        when(ossVideoService.doesObjectExist(anyString())).thenReturn(true);

        assertTrue(linkService.isOriginVideoExist("md5-exist"));
    }

    @Test
    void isOriginVideoExist_transcodeNotFinished_returnsFalse() {
        Transcode transcode = new Transcode();
        transcode.setId("tc_pending");
        transcode.setStatus(VideoStatus.TRANSCODING);
        transcode.setM3u8Key("videos/test/ts/pending.m3u8");
        mongoTemplate.save(transcode);

        Video video = new Video();
        video.setId("v_tc-pending");
        video.setStatus(VideoStatus.READY);
        video.setTranscodeIds(List.of(transcode.getId()));
        mongoTemplate.save(video);

        File file = new File();
        file.setId("f_tc-pending");
        file.setMd5("md5-tc-pending");
        file.setVideoId(video.getId());
        file.setKey("videos/test/raw/tcpending.mp4");
        file.setDeleted(false);
        mongoTemplate.save(file);

        when(ossVideoService.doesObjectExist(anyString())).thenReturn(true);

        assertFalse(linkService.isOriginVideoExist("md5-tc-pending"));
    }

    // ──────────────────── linkFile tests ────────────────────

    @Test
    void linkFile_setsLinkFieldsOnNewFile() {
        File oldFile = new File();
        oldFile.setId("f_old");
        oldFile.setKey("videos/old/raw/old.mp4");
        mongoTemplate.save(oldFile);

        File newFile = new File();
        newFile.setId("f_new");
        mongoTemplate.save(newFile);

        linkService.linkFile(newFile, oldFile);

        File updated = mongoTemplate.findById("f_new", File.class);
        assertNotNull(updated);
        assertTrue(updated.getHasLink());
        assertEquals("f_old", updated.getLinkFileId());
        assertEquals("videos/old/raw/old.mp4", updated.getLinkFileKey());
    }

    @Test
    void linkFile_doesNotModifyOldFile() {
        File oldFile = new File();
        oldFile.setId("f_old2");
        oldFile.setKey("videos/old/raw/old2.mp4");
        oldFile.setHasLink(false);
        mongoTemplate.save(oldFile);

        File newFile = new File();
        newFile.setId("f_new2");
        mongoTemplate.save(newFile);

        linkService.linkFile(newFile, oldFile);

        File oldFromDb = mongoTemplate.findById("f_old2", File.class);
        assertNotNull(oldFromDb);
        assertFalse(oldFromDb.getHasLink(),
                "Old file should not be modified by linkFile");
    }

    // ──────────────────── createFileAndVideoLink tests ────────────────────

    @Test
    void createFileAndVideoLink_linksFileAndVideo() {
        Video oldVideo = new Video();
        oldVideo.setId("v_old");
        oldVideo.setTranscodeIds(List.of("tc_1", "tc_2"));
        oldVideo.setCoverId("cover_old");
        oldVideo.setOwnerId("user_owner");
        mongoTemplate.save(oldVideo);

        File oldFile = new File();
        oldFile.setId("f_old-link");
        oldFile.setVideoId("v_old");
        oldFile.setKey("videos/old/raw/video.mp4");
        mongoTemplate.save(oldFile);

        Video newVideo = new Video();
        newVideo.setId("v_new");
        mongoTemplate.save(newVideo);

        File newFile = new File();
        newFile.setId("f_new-link");
        newFile.setMd5("matching-md5");
        mongoTemplate.save(newFile);

        linkService.createFileAndVideoLink(newVideo, newFile, oldFile);

        // Verify file link
        File updatedFile = mongoTemplate.findById("f_new-link", File.class);
        assertNotNull(updatedFile);
        assertTrue(updatedFile.getHasLink());
        assertEquals("f_old-link", updatedFile.getLinkFileId());
        assertEquals("videos/old/raw/video.mp4", updatedFile.getLinkFileKey());

        // Verify video link
        Video updatedVideo = mongoTemplate.findById("v_new", Video.class);
        assertNotNull(updatedVideo);
        assertTrue(updatedVideo.getLink().getHasLink());
        assertEquals("v_old", updatedVideo.getLink().getLinkVideoId());
        assertEquals(List.of("tc_1", "tc_2"), updatedVideo.getTranscodeIds());
        assertEquals("cover_old", updatedVideo.getCoverId());
        assertEquals("user_owner", updatedVideo.getOwnerId());
    }

    @Test
    void createFileAndVideoLink_preservesNewVideoUploaderId() {
        Video oldVideo = new Video();
        oldVideo.setId("v_old-keep");
        oldVideo.setUploaderId("original-uploader");
        oldVideo.setOwnerId("original-owner");
        oldVideo.setTranscodeIds(List.of("tc_keep"));
        mongoTemplate.save(oldVideo);

        File oldFile = new File();
        oldFile.setId("f_old-keep");
        oldFile.setVideoId("v_old-keep");
        oldFile.setKey("videos/old/raw/keep.mp4");
        mongoTemplate.save(oldFile);

        Video newVideo = new Video();
        newVideo.setId("v_new-keep");
        newVideo.setUploaderId("new-uploader");
        mongoTemplate.save(newVideo);

        File newFile = new File();
        newFile.setId("f_new-keep");
        mongoTemplate.save(newFile);

        linkService.createFileAndVideoLink(newVideo, newFile, oldFile);

        Video updatedVideo = mongoTemplate.findById("v_new-keep", Video.class);
        assertNotNull(updatedVideo);
        assertEquals("new-uploader", updatedVideo.getUploaderId(),
                "Uploader should remain the new video's uploader");
        assertEquals("original-owner", updatedVideo.getOwnerId(),
                "Owner should be set to old video's owner");
    }
}
