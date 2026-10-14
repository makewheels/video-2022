package com.github.makewheels.video2022.user;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.springboot.interceptor.requestlog.RequestLog;
import com.github.makewheels.video2022.springboot.interceptor.requestlog.RequestLogContext;
import com.github.makewheels.video2022.user.bean.User;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real pipeline/database, with narrowly injected failures; not a claim of a real Mongo outage. */
class RequestJournalFailureHttpContractTest extends BaseIntegrationTest {
    private static final String ID = "fixture-journal-user";
    private static final String TOKEN = "fixture-journal-token";

    @Autowired
    private WebApplicationContext context;

    @MockitoSpyBean
    private MongoTemplate tracedMongo;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        assertSame(mongoTemplate, tracedMongo);
        assertNull(UserHolder.get());
        assertNull(RequestLogContext.getRequestLog());
        cleanDatabase();
        User user = new User();
        user.setId(ID);
        user.setToken(TOKEN);
        user.setNickname("fixture-original");
        mongoTemplate.save(user);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        // The failure-path residue is an observed legacy defect, not state for another test.
        UserHolder.remove();
        RequestLogContext.removeRequestLog();
        cleanDatabase();
    }

    private JSONObject body(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertEquals(status, result.getResponse().getStatus());
        return JSON.parseObject(result.getResponse().getContentAsString());
    }

    private void failJournalSave() {
        doThrow(new IllegalStateException("fixture-journal-save-failure"))
                .when(tracedMongo).save(any(RequestLog.class));
    }

    @Test
    void failedAfterCompletionSaveKeepsSuccessResponseButLeavesJournalContext() throws Exception {
        failJournalSave();
        JSONObject response = body(get("/user/getMyProfile").header("token", TOKEN), 200);
        assertEquals(0, response.getIntValue("code"));
        assertEquals(ID, response.getJSONObject("data").getString("id"));
        assertTrue(mongoTemplate.findAll(RequestLog.class).isEmpty());
        assertNull(UserHolder.get());
        RequestLog residue = RequestLogContext.getRequestLog();
        assertNotNull(residue);
        assertEquals("/user/getMyProfile", residue.getRequestPath());
        assertNotNull(residue.getEndTime());
        assertEquals(200, residue.getResponse().getHttpStatus());
        JSONObject denied = body(get("/user/getMyProfile").header("token", "fixture-wrong"), 401);
        assertEquals(401, denied.getIntValue("code"));
        assertSame(residue, RequestLogContext.getRequestLog());
        assertNull(UserHolder.get());
    }

    @Test
    void businessWriteIsNotRolledBackWhenAfterCompletionJournalSaveFails() throws Exception {
        failJournalSave();
        JSONObject response = body(post("/user/updateProfile").header("token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"fixture-updated\"}"), 200);
        assertEquals(0, response.getIntValue("code"));
        assertEquals("fixture-updated", mongoTemplate.findById(ID, User.class).getNickname());
        assertTrue(mongoTemplate.findAll(RequestLog.class).isEmpty());
        assertNotNull(RequestLogContext.getRequestLog());
        assertNull(UserHolder.get());
    }

    @Test
    void dottedHeaderTriggersActualMapConversionFailureAfterBusinessWrite() throws Exception {
        JSONObject response = body(post("/user/updateProfile").header("token", TOKEN)
                .header("X.Fixture", "fixture-header-value")
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"fixture-updated\"}"), 200);
        assertEquals(0, response.getIntValue("code"));
        assertEquals("fixture-updated", mongoTemplate.findById(ID, User.class).getNickname());
        assertEquals(0, mongoTemplate.getCollection("requestLog").countDocuments());
        assertNotNull(RequestLogContext.getRequestLog());
        assertEquals("fixture-header-value", RequestLogContext.getRequestLog()
                .getRequest().getHeaderMap().get("X.Fixture"));
        assertNull(UserHolder.get());
    }

    @Test
    void dollarPrefixedHeaderPersistsWithItsOriginalName() throws Exception {
        JSONObject response = body(get("/user/getMyProfile").header("token", TOKEN)
                .header("$fixture", "fixture-header-value"), 200);
        assertEquals(0, response.getIntValue("code"));
        Document log = mongoTemplate.getCollection("requestLog").find().first();
        assertNotNull(log);
        assertEquals("fixture-header-value", log.get("request", Document.class)
                .get("headerMap", Document.class).getString("$fixture"));
        assertNull(RequestLogContext.getRequestLog());
        assertNull(UserHolder.get());
    }

    @Test
    void identityLookupFailureBeforeLoggerUsesHandledErrorWithoutJournalContext() throws Exception {
        doThrow(new IllegalStateException("fixture-identity-lookup-failure"))
                .when(tracedMongo).findOne(any(Query.class), eq(User.class));
        JSONObject response = body(get("/user/getMyProfile").header("token", TOKEN), 200);
        assertEquals(1, response.getIntValue("code"));
        assertEquals("未知错误", response.getString("message"));
        assertTrue(mongoTemplate.findAll(RequestLog.class).isEmpty());
        assertNull(UserHolder.get());
        assertNull(RequestLogContext.getRequestLog());
    }
}
