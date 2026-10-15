package com.github.makewheels.video2022.watch;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.video.bean.entity.Video;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Full MVC converters, exception advice and journals; external services remain mocked. */
class PlaylistHttpContractTest extends BaseIntegrationTest {
    private static final String MASTER = "/watchController/getMultivariantPlaylist.m3u8";
    private static final String CHILD = "/watchController/getM3u8Content.m3u8";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void video(String visibility) {
        Video video = new Video();
        video.setId("fixture-playlist-video");
        video.setUploaderId("fixture-owner");
        video.setVisibility(visibility);
        video.setTranscodeIds(List.of("fixture-playlist-task"));
        mongoTemplate.save(video);
    }

    private void task() {
        Transcode task = new Transcode();
        task.setId("fixture-playlist-task");
        task.setVideoId("fixture-playlist-video");
        task.setResolution("720p");
        task.setProvider("LOCAL_TRANSCODE");
        task.setStatus("FINISHED");
        task.setTsFileIds(List.of());
        task.setM3u8Content("#EXTM3U\r\nunknown.ts\n\n");
        mongoTemplate.save(task);
    }

    @Test
    void masterRetainsAcceptNegotiationInFullApplication() throws Exception {
        video("PUBLIC");
        task();
        for (String accept : List.of("*/*", "text/plain", "application/vnd.apple.mpegurl", "application/json")) {
            var response = mvc.perform(get(MASTER).param("videoId", "fixture-playlist-video")
                    .accept(accept)).andReturn().getResponse();
            assertEquals(200, response.getStatus());
            assertEquals("#EXTM3U\n", response.getContentAsString());
            String expected = "*/*".equals(accept) ? "text/plain" : accept;
            assertNotNull(response.getContentType());
            assertTrue(response.getContentType().startsWith(expected));
            System.out.println("PLAYLIST_HTTP_CONTRACT accept=" + accept
                    + " contentType=" + response.getContentType());
        }
        assertEquals(4, mongoTemplate.getCollection("requestLog").countDocuments());
    }

    @Test
    void childRetainsUnknownLinesAndLegacyTrailingNewlineRemoval() throws Exception {
        video("PUBLIC");
        task();
        var response = mvc.perform(get(CHILD).param("videoId", "fixture-playlist-video")
                .param("transcodeId", "fixture-playlist-task").param("resolution", "720p"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("#EXTM3U\r\nunknown.ts", response.getContentAsString());
        assertNotNull(response.getContentType());
        assertTrue(response.getContentType().startsWith("text/plain"));
        assertEquals(1, mongoTemplate.getCollection("requestLog").countDocuments());
    }

    @Test
    void absentVideoMasterReturnsEmptyBodyAndStillLogs() throws Exception {
        var response = mvc.perform(get(MASTER).param("videoId", "fixture-missing")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("", response.getContentAsString());
        assertEquals(1, mongoTemplate.getCollection("requestLog").countDocuments());
    }

    @Test
    void missingChildBindingUsesExistingGenericErrorEnvelope() throws Exception {
        var response = mvc.perform(get(CHILD).param("videoId", "fixture-playlist-video"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(1, JSON.parseObject(response.getContentAsString()).getIntValue("code"));
    }

    @Test
    void privateMasterUsesSignatureErrorEnvelope() throws Exception {
        video("PRIVATE");
        var response = mvc.perform(get(MASTER).param("videoId", "fixture-playlist-video"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        var body = JSON.parseObject(response.getContentAsString());
        assertEquals(35, body.getIntValue("code"));
        assertEquals("文件访问签名无效", body.getString("message"));
        assertEquals(1, mongoTemplate.getCollection("requestLog").countDocuments());
    }

    @Test
    void childRejectsForeignVideoBeforeReturningPlaylist() throws Exception {
        video("PUBLIC");
        task();
        var response = mvc.perform(get(CHILD).param("videoId", "fixture-foreign")
                .param("transcodeId", "fixture-playlist-task").param("resolution", "720p"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(35, JSON.parseObject(response.getContentAsString()).getIntValue("code"));
        assertEquals(1, mongoTemplate.getCollection("requestLog").countDocuments());
    }
}
