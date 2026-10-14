package com.github.makewheels.video2022.user;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.springboot.interceptor.requestlog.RequestLog;
import com.github.makewheels.video2022.springboot.interceptor.requestlog.RequestLogContext;
import com.github.makewheels.video2022.user.bean.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real interceptor order and persistence, using synthetic credentials in disposable Mongo only. */
class RequestContextHttpContractTest extends BaseIntegrationTest {
    private static final String ID = "fixture-context-user";
    private static final String TOKEN = "fixture-context-token";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        assertNull(UserHolder.get());
        assertNull(RequestLogContext.getRequestLog());
        User user = new User();
        user.setId(ID);
        user.setToken(TOKEN);
        mongoTemplate.save(user);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        assertNull(UserHolder.get());
        assertNull(RequestLogContext.getRequestLog());
        cleanDatabase();
    }

    private JSONObject body(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertEquals(status, result.getResponse().getStatus());
        return JSON.parseObject(result.getResponse().getContentAsString());
    }

    private RequestLog onlyLog() {
        assertEquals("requestLog", mongoTemplate.getCollectionName(RequestLog.class));
        var logs = mongoTemplate.findAll(RequestLog.class);
        assertEquals(1, logs.size());
        return logs.getFirst();
    }

    @Test
    void publicBusinessErrorStillPersistsRequestMetadataWithFirstHeaderValues() throws Exception {
        JSONObject response = body(get("/user/getChannel?userId=fixture-missing&token=fixture-query-token")
                .header("token", TOKEN, "fixture-second-token")
                .header("User-Agent", "fixture-first-agent", "fixture-second-agent")
                .header("X-Forwarded-For", "198.51.100.20")
                .with(request -> {
                    request.setRemoteAddr("192.0.2.10");
                    return request;
                }), 200);
        assertEquals(1, response.getIntValue("code"));
        RequestLog log = onlyLog();
        assertEquals("/user/getChannel", log.getRequestPath());
        assertEquals(log.getRequestPath(), log.getRequest().getPath());
        assertEquals("http://localhost/user/getChannel", log.getRequest().getUrl());
        assertEquals("GET", log.getRequest().getMethod());
        assertEquals("userId=fixture-missing&token=fixture-query-token", log.getRequest().getQueryString());
        assertEquals("192.0.2.10", log.getRequest().getIp());
        assertEquals("fixture-first-agent", log.getRequest().getUserAgent());
        assertEquals("fixture-first-agent", log.getRequest().getHeaderMap().get("User-Agent"));
        // This records current raw-header persistence, not a proposed logging policy.
        assertEquals(TOKEN, log.getRequest().getHeaderMap().get("token"));
        assertEquals(200, log.getResponse().getHttpStatus());
        assertNotNull(log.getStartTime());
        assertNotNull(log.getEndTime());
        assertTrue(log.getTimeCost() >= 0);
        assertEquals(log.getEndTime().getTime() - log.getStartTime().getTime(), log.getTimeCost());
        assertNull(log.getRequest().getBody());
        assertNull(log.getResponse().getBody());
        assertNull(log.getResponse().getHeaderMap());
        assertNull(UserHolder.get());
        assertNull(RequestLogContext.getRequestLog());
    }

    @Test
    void invalidHeaderOverridesValidQueryAndDenialDoesNotReachRequestLogger() throws Exception {
        JSONObject denied = body(get("/user/getMyProfile").header("token", "fixture-wrong")
                .param("token", TOKEN), 401);
        assertEquals(401, denied.getIntValue("code"));
        assertFalse(denied.containsKey("data"));
        assertTrue(mongoTemplate.findAll(RequestLog.class).isEmpty());
        assertNull(UserHolder.get());
        assertNull(RequestLogContext.getRequestLog());
        assertEquals(0, body(get("/user/getMyProfile").header("ToKeN", TOKEN), 200).getIntValue("code"));
        onlyLog();
        assertNull(UserHolder.get());
    }

    @Test
    void firstQueryTokenWinsAndEmptyHeaderFallsBackWithoutTrimming() throws Exception {
        assertEquals(401, body(get("/user/getMyProfile").header("token", "")
                .param("token", "fixture-wrong", TOKEN), 401).getIntValue("code"));
        assertEquals(401, body(get("/user/getMyProfile").header("token", " ")
                .param("token", TOKEN), 401).getIntValue("code"));
        assertTrue(mongoTemplate.findAll(RequestLog.class).isEmpty());
        JSONObject accepted = body(get("/user/getMyProfile").header("token", "")
                .param("token", TOKEN, "fixture-wrong"), 200);
        assertEquals(0, accepted.getIntValue("code"));
        assertNull(accepted.getJSONObject("data").get("token"));
        assertEquals(TOKEN, mongoTemplate.findById(ID, User.class).getToken());
        onlyLog();
    }

    @Test
    void handledMalformedJsonPersistsActualHttpStatusAndClearsBothContexts() throws Exception {
        JSONObject failed = body(post("/user/updateProfile").header("token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{"), 200);
        assertEquals(1, failed.getIntValue("code"));
        RequestLog log = onlyLog();
        assertEquals("POST", log.getRequest().getMethod());
        assertEquals(200, log.getResponse().getHttpStatus());
        assertNull(log.getRequest().getBody());
        assertNull(log.getResponse().getBody());
        assertNull(UserHolder.get());
        assertNull(RequestLogContext.getRequestLog());
    }

    @Test
    void successfulPostWritesBusinessStateButDoesNotCaptureBodyInRequestLog() throws Exception {
        JSONObject response = body(post("/user/updateProfile").header("token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"fixture-updated\"}"), 200);
        assertEquals(0, response.getIntValue("code"));
        assertEquals("fixture-updated", mongoTemplate.findById(ID, User.class).getNickname());
        RequestLog log = onlyLog();
        assertEquals("POST", log.getRequest().getMethod());
        assertNull(log.getRequest().getBody());
        assertNull(log.getResponse().getBody());
    }

    @Test
    void excludedPathsSkipPersistenceAndClearContextsEvenForHandledBindingErrors() throws Exception {
        assertEquals(1, body(get("/file/access").header("token", TOKEN), 200).getIntValue("code"));
        assertTrue(mongoTemplate.findAll(RequestLog.class).isEmpty());
        assertNull(UserHolder.get());
        assertNull(RequestLogContext.getRequestLog());
        assertEquals(1, body(post("/heartbeat/add").header("token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{"), 200).getIntValue("code"));
        assertTrue(mongoTemplate.findAll(RequestLog.class).isEmpty());
        assertNull(UserHolder.get());
        assertNull(RequestLogContext.getRequestLog());
    }

    @Test
    void emptyHeaderWithoutParameterStillMatchesAStoredEmptyToken() throws Exception {
        User user = mongoTemplate.findById(ID, User.class);
        user.setToken("");
        mongoTemplate.save(user);
        assertEquals(0, body(get("/user/getMyProfile").header("token", ""), 200).getIntValue("code"));
        assertEquals(401, body(get("/user/getMyProfile"), 401).getIntValue("code"));
        assertEquals(0, body(get("/user/getMyProfile").param("token", ""), 200).getIntValue("code"));
        assertEquals(0, body(get("/user/getMyProfile").header("token", "", "fixture-wrong"), 200)
                .getIntValue("code"));
        assertEquals(401, body(get("/user/getMyProfile").header("token", "fixture-wrong", ""), 401)
                .getIntValue("code"));
        assertEquals(3, mongoTemplate.findAll(RequestLog.class).size());
    }
}
