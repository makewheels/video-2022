package com.github.makewheels.video2022.utils;

import com.github.makewheels.video2022.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IdServiceTest extends BaseIntegrationTest {

    @Autowired
    private IdService idService;

    @Test
    void nextShortId_returnsValidBase36() {
        String shortId = idService.nextShortId();
        assertNotNull(shortId);
        assertFalse(shortId.isEmpty());
        // Result is uppercase base36 (digits + uppercase letters)
        assertTrue(shortId.matches("^[0-9A-Z]+$"),
                "shortId should be uppercase base36, got: " + shortId);
        // Length should be reasonable (typically 6-8 chars depending on date)
        assertTrue(shortId.length() >= 5 && shortId.length() <= 10,
                "shortId length out of expected range, got: " + shortId.length());
    }

    @Test
    void getUserId_startsWithPrefix() {
        String userId = idService.getUserId();
        assertNotNull(userId);
        assertTrue(userId.startsWith("u_"), "userId should start with 'u_', got: " + userId);
    }

    @Test
    void getVideoId_startsWithPrefix() {
        String videoId = idService.getVideoId();
        assertNotNull(videoId);
        assertTrue(videoId.startsWith("v_"), "videoId should start with 'v_', got: " + videoId);
    }

    @Test
    void getTranscodeId_startsWithPrefix() {
        String transcodeId = idService.getTranscodeId();
        assertNotNull(transcodeId);
        assertTrue(transcodeId.startsWith("tr_"), "transcodeId should start with 'tr_', got: " + transcodeId);
    }

    @Test
    void getCoverId_startsWithPrefix() {
        String coverId = idService.getCoverId();
        assertNotNull(coverId);
        assertTrue(coverId.startsWith("c_"), "coverId should start with 'c_', got: " + coverId);
    }

    @Test
    void getFileId_startsWithPrefix() {
        String fileId = idService.getFileId();
        assertNotNull(fileId);
        assertTrue(fileId.startsWith("f_"), "fileId should start with 'f_', got: " + fileId);
    }

    @Test
    void getTsFileId_startsWithPrefix() {
        String tsFileId = idService.getTsFileId();
        assertNotNull(tsFileId);
        assertTrue(tsFileId.startsWith("f_ts_"), "tsFileId should start with 'f_ts_', got: " + tsFileId);
    }

    @Test
    void generatedIds_areUnique() {
        Set<String> ids = new HashSet<>();
        int count = 50;
        for (int i = 0; i < count; i++) {
            ids.add(idService.nextLongId());
        }
        assertEquals(count, ids.size(), "All generated long IDs should be unique");

        Set<String> shortIds = new HashSet<>();
        for (int i = 0; i < count; i++) {
            shortIds.add(idService.nextShortId());
        }
        assertEquals(count, shortIds.size(), "All generated short IDs should be unique");
    }

    @Test
    void longId_lengthIsConsistent() {
        String id1 = idService.nextLongId();
        String id2 = idService.nextLongId();
        String id3 = idService.nextLongId();

        // All long IDs generated in the same time window should have the same length
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotNull(id3);
        assertEquals(id1.length(), id2.length(),
                "Long IDs should have consistent length");
        assertEquals(id2.length(), id3.length(),
                "Long IDs should have consistent length");
    }

    @Test
    void nextLongId_withCustomPrefix() {
        String id = idService.nextLongId("custom_");
        assertNotNull(id);
        assertTrue(id.startsWith("custom_"), "ID should start with custom prefix, got: " + id);
    }
}
