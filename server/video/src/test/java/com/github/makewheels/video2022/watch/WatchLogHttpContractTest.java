package com.github.makewheels.video2022.watch;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.user.bean.User;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Real controller, counters and logs; external IP lookup has explicit synthetic outcomes. */
class WatchLogHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mongoTemplate.getCollection("video").insertOne(new Document("_id", "fixture-video")
                .append("title", "fixture-title").append("status", "READY").append("visibility", "PRIVATE")
                .append("watch", new Document("watchCount", 0)));
        doReturn(location()).when(ipService).getIpInfo(anyString());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private JSONObject location() {
        return JSON.parseObject("{\"province\":\"fixture-province\",\"city\":\"fixture-city\","
                + "\"district\":\"fixture-district\",\"extra\":\"preserved\"}");
    }

    private MockHttpServletRequestBuilder request(String status, String client, String session) {
        var request = get("/watchController/addWatchLog").param("videoId", "fixture-video")
                .param("videoStatus", status).param("clientId", client).header("User-Agent", "fixture-agent")
                .header("X-Forwarded-For", "198.51.100.1");
        if (session != null) request.param("sessionId", session);
        request.with(req -> { req.setRemoteAddr("127.0.0.2"); return req; });
        return request;
    }

    private JSONObject call(MockHttpServletRequestBuilder request) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("application/json", response.getContentType());
        JSONObject body = JSON.parseObject(response.getContentAsString());
        assertEquals(java.util.Set.of("code", "message", "data"), body.keySet());
        assertNull(body.get("data"));
        int code = body.getIntValue("code");
        assertEquals(code == 0 ? "success" : code == 22 ? "视频不存在" : "未知错误", body.getString("message"));
        return body;
    }

    private long count() {
        return mongoTemplate.getCollection("video").find().first().get("watch", Document.class)
                .get("watchCount", Number.class).longValue();
    }

    @Test
    void anonymousPrivateVideoAndOtherViewerShareDedupByVideoSessionAndStatus() throws Exception {
        assertEquals(0, call(request("READY", "first", "same-session")).getIntValue("code"));
        User user = new User();
        user.setId("fixture-watch-viewer");
        user.setToken("fixture-watch-token");
        mongoTemplate.save(user);
        assertEquals(0, call(request("READY", "other", "same-session").header("token", user.getToken()))
                .getIntValue("code"));
        assertEquals(1, count());
        assertInstanceOf(Long.class, mongoTemplate.getCollection("video").find().first()
                .get("watch", Document.class).get("watchCount"));
        assertEquals(1, mongoTemplate.getCollection("watchLog").countDocuments());
        Document log = mongoTemplate.getCollection("watchLog").find().first();
        assertEquals("first", log.getString("clientId"));
        assertFalse(log.containsKey("viewerId"));
        assertEquals("127.0.0.2", log.getString("ip"));
        assertEquals("fixture-agent", log.getString("userAgent"));
        assertEquals("preserved", log.get("ipInfo", Document.class).getString("extra"));
        assertNotNull(log.getDate("createTime"));
        verify(ipService, times(2)).getIpInfo("127.0.0.2");
    }

    @Test
    void requestStatusControlsCounterButActualStatusIsStoredSoMismatchDoesNotDedup() throws Exception {
        mongoTemplate.getCollection("video").updateOne(new Document("_id", "fixture-video"),
                new Document("$set", new Document("status", "TRANSCODING")));
        for (int i = 0; i < 2; i++) {
            assertEquals(0, call(request("READY", "client", "session")).getIntValue("code"));
        }
        assertEquals(2, count());
        assertEquals(2, mongoTemplate.getCollection("watchLog").countDocuments());
        for (Document log : mongoTemplate.getCollection("watchLog").find()) {
            assertEquals("TRANSCODING", log.getString("videoStatus"));
        }
        verify(ipService, times(4)).getIpInfo("127.0.0.2");
    }

    @Test
    void firstIpFailureKeepsCounterWithoutLogAndLaterRequestCanIncrementAgain() throws Exception {
        doThrow(new IllegalStateException("synthetic first IP failure")).when(ipService).getIpInfo(anyString());
        assertEquals(1, call(request("READY", "client", "session")).getIntValue("code"));
        assertEquals(1, count());
        assertEquals(0, mongoTemplate.getCollection("watchLog").countDocuments());
        doReturn(location()).when(ipService).getIpInfo(anyString());
        assertEquals(0, call(request("READY", "client", "session")).getIntValue("code"));
        assertEquals(2, count());
        assertEquals(1, mongoTemplate.getCollection("watchLog").countDocuments());
    }

    @Test
    void secondIpFailureKeepsLogAndCounterThenDuplicateReturnsSuccess() throws Exception {
        doReturn(location()).doThrow(new IllegalStateException("synthetic second IP failure"))
                .when(ipService).getIpInfo(anyString());
        assertEquals(1, call(request("READY", "client", "session")).getIntValue("code"));
        assertEquals(1, count());
        assertEquals(1, mongoTemplate.getCollection("watchLog").countDocuments());
        assertEquals(0, call(request("READY", "client", "session")).getIntValue("code"));
        assertEquals(1, count());
        verify(ipService, times(2)).getIpInfo("127.0.0.2");
    }

    @Test
    void missingSessionSharesNullDedupAndNonReadyRequestDoesNotIncrement() throws Exception {
        assertEquals(0, call(request("READY", "first", null)).getIntValue("code"));
        assertEquals(0, call(request("READY", "other", null)).getIntValue("code"));
        assertEquals(1, count());
        assertEquals(1, mongoTemplate.getCollection("watchLog").countDocuments());
        assertEquals(0, call(request("PAUSED", "client", "other-session")).getIntValue("code"));
        assertEquals(1, count());
        assertEquals(2, mongoTemplate.getCollection("watchLog").countDocuments());
    }

    @Test
    void controllerExistenceCheckPrecedesDedupAndMissingStatusNeverWrites() throws Exception {
        mongoTemplate.getCollection("watchLog").insertOne(new Document("videoId", "missing")
                .append("sessionId", "session").append("videoStatus", "READY"));
        var missing = request("READY", "client", "session");
        missing.with(req -> { req.setParameter("videoId", "missing"); return req; });
        assertEquals(22, call(missing).getIntValue("code"));
        var noStatus = request("READY", "client", "session");
        noStatus.with(req -> { req.removeParameter("videoStatus"); return req; });
        assertEquals(1, call(noStatus).getIntValue("code"));
        assertEquals(0, count());
        assertEquals(1, mongoTemplate.getCollection("watchLog").countDocuments());
        verifyNoInteractions(ipService);
    }
    @Test
    void ipJsonPreservesNullNestedDecimalAndLongBsonTypes() throws Exception {
        doReturn(JSON.parseObject("{\"province\":\"fixture\",\"nullable\":null,\"decimal\":1.2500,"
                + "\"long\":2147483648,\"nested\":{\"value\":2},\"list\":[null,1.50]}"))
                .when(ipService).getIpInfo(anyString());
        assertEquals(0, call(request("READY", "client", "session")).getIntValue("code"));
        Document info = mongoTemplate.getCollection("watchLog").find().first().get("ipInfo", Document.class);
        assertTrue(info.containsKey("nullable"));
        assertNull(info.get("nullable"));
        assertEquals(new java.math.BigDecimal("1.2500"),
                info.get("decimal", org.bson.types.Decimal128.class).bigDecimalValue());
        assertInstanceOf(Long.class, info.get("long"));
        assertEquals(2, info.get("nested", Document.class).getInteger("value"));
        assertNull(info.getList("list", Object.class).get(0));
        assertInstanceOf(org.bson.types.Decimal128.class, info.getList("list", Object.class).get(1));
    }

    @Test
    void dottedIpMapFailsAfterIncrementBeforeLogSave() throws Exception {
        doReturn(JSON.parseObject("{\"nested\":{\"fixture.dot\":\"value\"}}"))
                .when(ipService).getIpInfo(anyString());
        assertEquals(1, call(request("READY", "client", "session")).getIntValue("code"));
        assertEquals(1, count());
        assertEquals(0, mongoTemplate.getCollection("watchLog").countDocuments());
        verify(ipService).getIpInfo("127.0.0.2");
    }

    @Test
    void uppercaseHexRequestStoresCanonicalVideoIdSoOriginalCaseDoesNotDedup() throws Exception {
        String id = "64B0000000000000000000AB";
        mongoTemplate.getCollection("video").insertOne(new Document("_id", new org.bson.types.ObjectId(id))
                .append("status", "READY").append("watch", new Document("watchCount", 0)));
        for (int i = 0; i < 2; i++) {
            var builder = request("READY", "client", "session");
            builder.with(req -> { req.setParameter("videoId", id); return req; });
            assertEquals(0, call(builder).getIntValue("code"));
        }
        assertEquals(2, mongoTemplate.getCollection("watchLog").countDocuments());
        for (Document event : mongoTemplate.getCollection("watchLog").find()) {
            assertEquals(id.toLowerCase(), event.getString("videoId"));
        }
        Document video = mongoTemplate.getCollection("video").find(
                new Document("_id", new org.bson.types.ObjectId(id))).first();
        assertEquals(2L, video.get("watch", Document.class).getLong("watchCount"));
    }

}
