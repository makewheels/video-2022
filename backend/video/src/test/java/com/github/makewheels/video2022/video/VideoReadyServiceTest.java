package com.github.makewheels.video2022.video;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.service.VideoReadyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link VideoReadyService}.
 */
class VideoReadyServiceTest extends BaseIntegrationTest {

    @Autowired
    private VideoReadyService videoReadyService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private Video createVideoInDb(String id, boolean hasLink, String rawFileId) {
        Video video = new Video();
        video.setId(id);
        video.setTitle("Test Video");
        video.setStatus(VideoStatus.READY);
        video.setRawFileId(rawFileId);
        video.getLink().setHasLink(hasLink);
        mongoTemplate.save(video);
        return video;
    }

    private File createFileInDb(String id, String key) {
        File file = new File();
        file.setId(id);
        file.setKey(key);
        mongoTemplate.save(file);
        return file;
    }

    // ──────────────────── onVideoReady tests ────────────────────

    @Test
    void onVideoReady_nonLinkedVideo_changesStorageClassToIA() {
        File file = createFileInDb("f_ready-1", "videos/test/raw/video.mp4");
        createVideoInDb("v_ready-1", false, file.getId());

        videoReadyService.onVideoReady("v_ready-1");

        File updatedFile = mongoTemplate.findById(file.getId(), File.class);
        assertNotNull(updatedFile);
        assertEquals("IA", updatedFile.getStorageClass(),
                "Non-linked video raw file should be changed to IA storage class");
    }

    @Test
    void onVideoReady_linkedVideo_doesNotChangeStorageClass() {
        File file = createFileInDb("f_linked-1", "videos/test/raw/linked.mp4");
        createVideoInDb("v_linked-1", true, file.getId());

        videoReadyService.onVideoReady("v_linked-1");

        File updatedFile = mongoTemplate.findById(file.getId(), File.class);
        assertNotNull(updatedFile);
        assertNull(updatedFile.getStorageClass(),
                "Linked video should NOT change raw file storage class");
    }
}
