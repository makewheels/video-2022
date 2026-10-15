package com.github.makewheels.video2022.watch;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.user.bean.User;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real MVC and isolated Mongo; no production heartbeat or external requests. */
class HeartbeatProgressHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;
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

    private JSONObject response(MockHttpServletRequestBuilder request) throws Exception {
        var result = mvc.perform(request).andReturn().getResponse();
        assertEquals(200, result.getStatus());
        return JSON.parseObject(result.getContentAsString());
    }

    private MockHttpServletRequestBuilder heartbeat(String body) {
        return post("/heartbeat/add").contentType("application/json").content(body);
    }

    private Document progress() {
        assertEquals(1, mongoTemplate.getCollection("progress").countDocuments());
        return mongoTemplate.getCollection("progress").find().first();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLAYING", "paused", "PAUSED", "", " playing"})
    void otherStatusesSaveHeartbeatWithoutProgress(String status) throws Exception {
        var body = new JSONObject();
        body.put("videoId", "fixture-unknown-video");
        body.put("clientId", "fixture-client");
        body.put("playerStatus", status);
        body.put("playerTime", 12000);
        assertEquals(0, response(heartbeat(body.toJSONString())).getIntValue("code"));
        assertEquals(1, mongoTemplate.getCollection("heartbeat").countDocuments());
        assertEquals(0, mongoTemplate.getCollection("progress").countDocuments());
    }

    @Test
    void anonymousBodyViewerIsPreservedAndCanGroupDifferentClients() throws Exception {
        for (String client : new String[]{"first", "second"}) {
            String body = "{\"videoId\":\"unknown\",\"viewerId\":\"fixture-body-viewer\","
                    + "\"clientId\":\"" + client + "\",\"sessionId\":\"" + client + "\","
                    + "\"playerStatus\":\"playing\",\"playerTime\":-7}";
            assertEquals(0, response(heartbeat(body)).getIntValue("code"));
        }
        Document saved = progress();
        assertEquals("fixture-body-viewer", saved.getString("viewerId"));
        assertEquals("first", saved.getString("clientId"));
        assertEquals("second", saved.getString("sessionId"));
        assertEquals(-7L, saved.getLong("progressInMillis"));
        assertNotNull(saved.getDate("createTime"));
        assertNotNull(saved.getDate("updateTime"));
        assertEquals(2, mongoTemplate.getCollection("heartbeat").countDocuments());
    }

    @Test
    void tokenOverridesBodyViewerAndProgressGetUsesAuthenticatedViewer() throws Exception {
        User user = new User();
        user.setId("fixture-auth-viewer");
        user.setToken("fixture-heartbeat-token");
        mongoTemplate.save(user);
        String body = "{\"videoId\":\"unknown\",\"viewerId\":\"body-viewer\","
                + "\"clientId\":\"first\",\"playerStatus\":\"playing\",\"playerTime\":12345}";
        assertEquals(0, response(heartbeat(body).header("token", user.getToken())).getIntValue("code"));
        assertEquals(user.getId(), progress().getString("viewerId"));
        assertEquals(user.getId(), mongoTemplate.getCollection("heartbeat").find().first().getString("viewerId"));
        var result = response(get("/progress/getProgress").param("videoId", "unknown")
                .param("clientId", "other-device").header("token", user.getToken()));
        assertEquals(0, result.getIntValue("code"));
        assertEquals(12345L, result.getJSONObject("data").getLongValue("progressInMillis"));
        var absent = response(get("/progress/getProgress").param("videoId", "absent").param("clientId", "first"));
        assertEquals(0, absent.getIntValue("code"));
        assertNull(absent.get("data"));
    }

    @Test
    void missingIdentityFailsAfterHeartbeatWasAlreadySaved() throws Exception {
        var result = response(heartbeat("{\"videoId\":\"unknown\",\"playerStatus\":\"playing\"}"));
        assertEquals(1, result.getIntValue("code"));
        assertEquals(1, mongoTemplate.getCollection("heartbeat").countDocuments());
        assertEquals(0, mongoTemplate.getCollection("progress").countDocuments());
        assertEquals(1, response(get("/progress/getProgress").param("videoId", "unknown")).getIntValue("code"));
    }

    @Test
    void clientOnlyCanReplaceProgressWithNullAndKeepsOriginalCreationTime() throws Exception {
        String start = "{\"videoId\":\"unknown\",\"clientId\":\"fixture-client\","
                + "\"playerStatus\":\"playing\",\"playerTime\":100}";
        assertEquals(0, response(heartbeat(start)).getIntValue("code"));
        Document before = progress();
        assertEquals(0, response(heartbeat(start.replace(",\"playerTime\":100", ""))).getIntValue("code"));
        Document after = progress();
        assertEquals(before.get("_id"), after.get("_id"));
        assertEquals(before.getDate("createTime"), after.getDate("createTime"));
        assertNull(after.get("progressInMillis"));
        var result = response(get("/progress/getProgress").param("videoId", "unknown")
                .param("clientId", "fixture-client"));
        assertEquals(0, result.getIntValue("code"));
        assertNotNull(result.getJSONObject("data"));
        assertNull(result.getJSONObject("data").get("progressInMillis"));
    }

    @Test
    void invalidLongDoesNotPersistHeartbeatOrProgress() throws Exception {
        var result = response(heartbeat("{\"clientId\":\"c\",\"playerStatus\":\"playing\",\"playerTime\":{}}"));
        assertEquals(1, result.getIntValue("code"));
        assertEquals(0, mongoTemplate.getCollection("heartbeat").countDocuments());
        assertEquals(0, mongoTemplate.getCollection("progress").countDocuments());
    }
    @Test
    void progressReplacementDropsUnknownFieldsAndOmitsNullsWithLegacyBsonTypes() throws Exception {
        String body = "{\"videoId\":\"unknown\",\"clientId\":\"c\","
                + "\"playerStatus\":\"playing\",\"sessionId\":\"s\",\"playerTime\":42}";
        assertEquals(0, response(heartbeat(body)).getIntValue("code"));
        Document original = progress();
        assertInstanceOf(org.bson.types.ObjectId.class, original.get("_id"));
        assertInstanceOf(Long.class, original.get("progressInMillis"));
        assertFalse(original.containsKey("_class"));
        assertFalse(original.containsKey("viewerId"));
        mongoTemplate.getCollection("progress").updateOne(new Document("_id", original.get("_id")),
                new Document("$set", new Document("fixtureExtra", "drop-me")));
        assertEquals(0, response(heartbeat(body.replace(",\"sessionId\":\"s\",\"playerTime\":42", "")))
                .getIntValue("code"));
        Document replaced = progress();
        assertEquals(original.get("_id"), replaced.get("_id"));
        assertEquals(original.get("createTime"), replaced.get("createTime"));
        assertFalse(replaced.containsKey("fixtureExtra"));
        assertFalse(replaced.containsKey("sessionId"));
        assertFalse(replaced.containsKey("progressInMillis"));
    }

}
