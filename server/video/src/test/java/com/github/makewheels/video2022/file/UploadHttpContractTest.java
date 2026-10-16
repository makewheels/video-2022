package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.utils.IdService;
import com.github.makewheels.video2022.video.service.RawFileService;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Old upload HTTP rules and real Mongo writes; all cloud and worker submissions are synthetic. */
class UploadHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;
    @MockitoBean private IdService ids;
    @MockitoBean private RawFileService worker;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mongoTemplate.getCollection("user").insertOne(new Document("_id", "fixture-uploader")
                .append("token", "fixture-upload-token"));
        when(ids.getVideoId()).thenReturn("fixture-video");
        when(ids.getFileId()).thenReturn("fixture-file");
        when(ids.nextShortId()).thenReturn("fixture-watch");
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private JSONObject call(MockHttpServletRequestBuilder request, boolean login) throws Exception {
        if (login) request.header("token", "fixture-upload-token");
        var response = mvc.perform(request).andReturn().getResponse();
        assertEquals(login ? 200 : 401, response.getStatus());
        assertEquals(login ? "application/json" : "application/json;charset=UTF-8", response.getContentType());
        JSONObject body = JSON.parseObject(response.getContentAsString());
        assertEquals(login ? java.util.Set.of("code", "message", "data") : java.util.Set.of("code", "message"),
                body.keySet());
        return body;
    }

    private JSONObject create(String body) throws Exception {
        return call(post("/video/create").contentType("application/json").content(body), true);
    }

    private JSONObject create() throws Exception {
        return create("{\"videoType\":\"USER_UPLOAD\",\"rawFilename\":\"Fixture.MP4\",\"size\":42}");
    }

    private Document file() {
        return mongoTemplate.getCollection("file").find().first();
    }

    private Document video() {
        return mongoTemplate.getCollection("video").find().first();
    }

    @Test
    void allFourEntrypointsRequireLoginBeforeBindingAndNeverCallCloud() throws Exception {
        assertEquals(401, call(post("/video/create"), false).getIntValue("code"));
        for (String path : new String[]{"/file/getUploadCredentials", "/file/uploadFinish", "/video/rawFileUploadFinish"}) {
            assertEquals(401, call(get(path), false).getIntValue("code"));
        }
        assertEquals(0, mongoTemplate.getCollection("requestLog").countDocuments());
        assertEquals(0, mongoTemplate.getCollection("exceptionLog").countDocuments());
        assertNull(file());
        assertNull(video());
        verifyNoInteractions(ossVideoService, worker);
    }

    @Test
    void userUploadCreationKeepsResponseAndDoesNotTreatDeclaredSizeAsStoredObjectSize() throws Exception {
        JSONObject result = create();
        assertEquals(0, result.getIntValue("code"));
        assertEquals("video created", result.getString("message"));
        assertEquals(JSON.parseObject("{\"fileId\":\"fixture-file\",\"videoId\":\"fixture-video\","
                + "\"watchId\":\"fixture-watch\",\"watchUrl\":\"http://localhost:5022/w?v=fixture-watch\","
                + "\"shortUrl\":\"http://localhost:5022/w?v=fixture-watch\"}"), result.getJSONObject("data"));
        assertEquals("fixture-uploader", video().getString("ownerId"));
        assertEquals("fixture-uploader", video().getString("uploaderId"));
        assertEquals("PUBLIC", video().getString("visibility"));
        assertEquals("AUTO", video().getString("transcodeMode"));
        assertEquals("CREATED", video().getString("status"));
        assertEquals("CREATED", file().getString("fileStatus"));
        assertEquals("mp4", file().getString("extension"));
        assertEquals("Fixture.MP4", file().getString("rawFilename"));
        assertFalse(file().containsKey("size"));
        assertTrue(file().getString("key").matches(
                "videos/fixture-uploader/[0-9]{6}/fixture-video/raw/fixture-file/fixture-file.mp4"));
        assertEquals(1, mongoTemplate.getCollection("requestLog").countDocuments());
        verifyNoInteractions(ossVideoService, worker);
    }

    @Test
    void sizeZeroIsRejectedButNegativeSizeAndOnlyExactLocalModeRemainLegacyBehavior() throws Exception {
        for (String body : new String[]{"{}", "{\"videoType\":\"USER_UPLOAD\",\"rawFilename\":\"a\"}",
                "{\"videoType\":\"USER_UPLOAD\",\"rawFilename\":\"a\",\"size\":0}"}) {
            assertEquals(21, create(body).getIntValue("code"));
        }
        assertNull(file());
        for (String mode : new String[]{"LOCAL", "local", "other"}) {
            assertEquals(0, create("{\"videoType\":\"USER_UPLOAD\",\"rawFilename\":\"a.MP4\","
                    + "\"size\":-1,\"transcodeMode\":\"" + mode + "\"}").getIntValue("code"));
            assertEquals(mode.equals("LOCAL") ? "LOCAL" : "AUTO", video().getString("transcodeMode"));
            assertFalse(file().containsKey("size"));
        }
        verifyNoInteractions(ossVideoService, worker);
    }

    @Test
    void credentialsKeepFieldNamesProviderAndLegacyNullFailureWithoutChangingFile() throws Exception {
        create();
        Document before = file();
        JSONObject credentials = JSON.parseObject("{\"bucket\":\"fixture-bucket\",\"key\":\"fixture-key\","
                + "\"endpoint\":\"https://example.invalid\",\"accessKeyId\":\"fixture-not-real\","
                + "\"secretKey\":\"fixture-not-real\",\"sessionToken\":\"fixture-not-real\","
                + "\"expiration\":\"2030-01-01T00:00:00Z\"}");
        when(ossVideoService.generateUploadCredentials(before.getString("key"))).thenReturn(credentials);
        JSONObject result = call(get("/file/getUploadCredentials").param("fileId", "fixture-file"), true);
        assertEquals(0, result.getIntValue("code"));
        assertEquals("ALIYUN_OSS", result.getJSONObject("data").getString("provider"));
        assertEquals("fixture-not-real", result.getJSONObject("data").getString("secretKey"));
        assertEquals(before, file());
        when(ossVideoService.generateUploadCredentials(anyString())).thenReturn(null);
        assertEquals(34, call(get("/file/getUploadCredentials").param("fileId", "fixture-file"), true).getIntValue("code"));
        assertEquals(before, file());
        verify(ossVideoService, times(2)).generateUploadCredentials(before.getString("key"));
    }

    @Test
    void missingAndOtherOwnersFileNeverRequestsCredentialsOrMetadata() throws Exception {
        assertEquals(31, call(get("/file/getUploadCredentials").param("fileId", "absent"), true).getIntValue("code"));
        create();
        mongoTemplate.getCollection("file").updateOne(new Document("_id", "fixture-file"),
                new Document("$set", new Document("uploaderId", "other-user")));
        for (String path : new String[]{"/file/getUploadCredentials", "/file/uploadFinish"}) {
            assertEquals(33, call(get(path).param("fileId", "fixture-file"), true).getIntValue("code"));
        }
        verifyNoInteractions(ossVideoService, worker);
    }

    @Test
    void uploadFinishUsesRemoteMetadataAndRemoteFailureDoesNotAlterFile() throws Exception {
        create();
        Document before = file();
        when(ossVideoService.getObject(anyString())).thenThrow(new IllegalStateException("synthetic OSS failure"));
        assertEquals(1, call(get("/file/uploadFinish").param("fileId", "fixture-file"), true).getIntValue("code"));
        assertEquals(before, file());
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(123L);
        metadata.setLastModified(new Date(1000));
        metadata.setHeader("ETag", "fixture-etag");
        OSSObject object = new OSSObject();
        object.setObjectMetadata(metadata);
        doReturn(object).when(ossVideoService).getObject(anyString());
        assertEquals(0, call(get("/file/uploadFinish").param("fileId", "fixture-file"), true).getIntValue("code"));
        assertEquals("READY", file().getString("fileStatus"));
        assertEquals(123L, file().getLong("size"));
        assertEquals(new Date(1000), file().getDate("uploadTime"));
        assertEquals("fixture-etag", file().getString("etag"));
        verify(ossVideoService, times(2)).getObject(before.getString("key"));
        verifyNoInteractions(worker);
    }

    @Test
    void rawCompletionValidatesExistingStateThenAcceptsAnotherAuthenticatedViewerWithoutOwnershipCheck() throws Exception {
        create();
        assertEquals(32, call(get("/video/rawFileUploadFinish").param("videoId", "fixture-video"), true).getIntValue("code"));
        verifyNoInteractions(worker);
        mongoTemplate.getCollection("file").updateOne(new Document("_id", "fixture-file"),
                new Document("$set", new Document("fileStatus", "READY")));
        mongoTemplate.getCollection("video").updateOne(new Document("_id", "fixture-video"),
                new Document("$set", new Document("uploaderId", "other-user")));
        CountDownLatch called = new CountDownLatch(1);
        doAnswer(invocation -> { called.countDown(); return null; }).when(worker).onRawFileUploadFinish("fixture-video");
        assertEquals(0, call(get("/video/rawFileUploadFinish").param("videoId", "fixture-video"), true).getIntValue("code"));
        assertTrue(called.await(2, TimeUnit.SECONDS));
        verify(worker, times(1)).onRawFileUploadFinish("fixture-video");
        mongoTemplate.getCollection("video").updateOne(new Document("_id", "fixture-video"),
                new Document("$set", new Document("status", "READY")));
        assertEquals(25, call(get("/video/rawFileUploadFinish").param("videoId", "fixture-video"), true).getIntValue("code"));
        verifyNoMoreInteractions(worker);
    }
}
