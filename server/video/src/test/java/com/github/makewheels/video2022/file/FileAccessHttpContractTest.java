package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.access.FileAccessLogService;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.video.bean.entity.Video;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Full MVC + isolated Mongo; observe real asynchronous work without injecting a failure. */
class FileAccessHttpContractTest extends BaseIntegrationTest {
    private static final String VIDEO = "fixture-access-video";
    private static final String FILE = "fixture-access-file";
    private static final String KEY = "fixture/segment.ts";
    private static final String URL = "https://example.invalid/fixture?Signature=synthetic";
    @Autowired private WebApplicationContext context;
    @Autowired private FileAccessSignatureService signatures;
    @MockitoSpyBean private FileAccessLogService accessLogs;
    private MockMvc mvc;
    private CountDownLatch done;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicReference<RequestAttributes> attributes = new AtomicReference<>();
    private final AtomicReference<Thread> worker = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        cleanDatabase();
        done = new CountDownLatch(0);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        assertTrue(done.await(5, TimeUnit.SECONDS), "Wait for all observed work before clearing Mongo");
        cleanDatabase();
    }

    private MockHttpServletRequestBuilder request(String timestamp, String fileId) {
        String sign = signatures.generateSignature(VIDEO, "c", "s", "720p", fileId, timestamp, "fixture-nonce");
        return get("/file/access").param("videoId", VIDEO).param("clientId", "c").param("sessionId", "s")
                .param("resolution", "720p").param("fileId", fileId).param("timestamp", timestamp)
                .param("nonce", "fixture-nonce").param("sign", sign);
    }

    private void observe() {
        done = new CountDownLatch(1);
        doAnswer(invocation -> {
            worker.set(Thread.currentThread());
            attributes.set(RequestContextHolder.getRequestAttributes());
            try {
                return invocation.callRealMethod();
            } catch (Throwable error) {
                failure.set(error);
                throw error;
            } finally {
                done.countDown();
            }
        }).when(accessLogs).handleAccessLog(any(HttpServletRequest.class), eq(VIDEO), eq("c"),
                eq("s"), eq("720p"), anyString());
    }

    private void documents() {
        Video video = new Video();
        video.setId(VIDEO);
        video.setOwnerId("fixture-owner");
        mongoTemplate.save(video);
        Transcode task = new Transcode();
        task.setId("fixture-access-task");
        task.setVideoId(VIDEO);
        task.setResolution("720p");
        mongoTemplate.save(task);
        TsFile file = new TsFile();
        file.setId(FILE);
        file.setVideoId(VIDEO);
        file.setTranscodeId(task.getId());
        file.setResolution("720p");
        file.setKey(KEY);
        file.setFileType("TS");
        file.setSize(100L);
        mongoTemplate.save(file);
    }

    @Test
    void redirectSurvivesRealAsyncMissingContextWithoutLoggingOrCharging() throws Exception {
        documents();
        observe();
        when(ossVideoService.generatePresignedUrl(KEY, Duration.ofHours(3))).thenReturn(URL);
        Thread caller = Thread.currentThread();
        var response = mvc.perform(request(Long.toString(System.currentTimeMillis()), FILE)).andReturn().getResponse();
        assertEquals(302, response.getStatus());
        assertEquals(URL, response.getHeader("Location"));
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertNotSame(caller, worker.get());
        assertNull(attributes.get());
        assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals("当前线程无法获取请求上下文，可能在异步线程中调用", failure.get().getMessage());
        assertEquals(0, mongoTemplate.getCollection("fileAccessLog").countDocuments());
        assertEquals(0, mongoTemplate.getCollection("ossAccessFee").countDocuments());
        assertEquals(0, mongoTemplate.getCollection("requestLog").countDocuments());
        verify(ossVideoService).generatePresignedUrl(KEY, Duration.ofHours(3));
        assertEquals("application/json", response.getContentType());
        assertEquals(JSON.parseObject("{\"code\":0,\"data\":null,\"message\":\"success\"}"),
                JSON.parseObject(response.getContentAsString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolution", "fileId", "timestamp", "nonce", "sign"})
    void missingRequiredParameterNeverSignsOrSchedules(String missing) throws Exception {
        var builder = request(Long.toString(System.currentTimeMillis()), FILE);
        builder.with(req -> { req.removeParameter(missing); return req; });
        var response = mvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(1, JSON.parseObject(response.getContentAsString()).getIntValue("code"));
        verifyNoInteractions(ossVideoService, accessLogs);
        assertEquals(0, mongoTemplate.getCollection("requestLog").countDocuments());
    }

    @Test
    void timestampFailurePrecedesSignatureAndFileLookup() throws Exception {
        var builder = request("0", FILE);
        builder.with(req -> { req.setParameter("sign", "wrong"); return req; });
        var response = mvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        var body = JSON.parseObject(response.getContentAsString());
        assertEquals(35, body.getIntValue("code"));
        assertEquals("文件访问签名无效", body.getString("message"));
        assertEquals("文件访问签名已过期",
                mongoTemplate.getCollection("exceptionLog").find().first().getString("exceptionMessage"));
        verifyNoInteractions(ossVideoService, accessLogs);
    }

    @Test
    void tamperedSignatureNeverSignsOrSchedules() throws Exception {
        var builder = request(Long.toString(System.currentTimeMillis()), FILE);
        builder.with(req -> { req.setParameter("sign", "wrong"); return req; });
        var response = mvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(35, JSON.parseObject(response.getContentAsString()).getIntValue("code"));
        verifyNoInteractions(ossVideoService, accessLogs);
    }

    @Test
    void missingFileStillSchedulesRealWorkBeforeReturningCode31() throws Exception {
        observe();
        var response = mvc.perform(request(Long.toString(System.currentTimeMillis()), "fixture-missing"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(31, JSON.parseObject(response.getContentAsString()).getIntValue("code"));
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertInstanceOf(NullPointerException.class, failure.get());
        verifyNoInteractions(ossVideoService);
        assertEquals(0, mongoTemplate.getCollection("requestLog").countDocuments());
    }
}
