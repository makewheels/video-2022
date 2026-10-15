package com.github.makewheels.video2022.watch;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.user.bean.User;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Legacy session lifecycle through actual MVC converters/interceptors and isolated Mongo. */
class PlaybackSessionHttpContractTest extends BaseIntegrationTest {
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

    private JSONObject call(String route, String body, String contentType, String token) throws Exception {
        var request = post("/playback/" + route).contentType(contentType).content(body);
        if (token != null) request.header("token", token);
        var response = mvc.perform(request).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("application/json", response.getContentType());
        return JSON.parseObject(response.getContentAsString());
    }

    private JSONObject call(String route, String body) throws Exception {
        return call(route, body, "application/json", null);
    }

    private String start() throws Exception {
        var response = call("start", "{\"videoId\":\"unknown\",\"clientId\":\"c\",\"sessionId\":\"s\"}");
        assertEquals(0, response.getIntValue("code"));
        return response.getJSONObject("data").getString("playbackSessionId");
    }

    private Document session(String id) {
        return mongoTemplate.getCollection("playbackSession").find(new Document("_id", new ObjectId(id))).first();
    }

    @Test
    void startAcceptsMissingFieldsAndRepeatedCallsCreateNewSessionsWithBsonDefaults() throws Exception {
        var first = call("start", "{}");
        var second = call("start", "{}");
        assertEquals(0, first.getIntValue("code"));
        String id = first.getJSONObject("data").getString("playbackSessionId");
        assertNotEquals(id, second.getJSONObject("data").getString("playbackSessionId"));
        Document saved = session(id);
        assertEquals(0L, saved.getLong("totalPlayDurationMs"));
        assertEquals(0L, saved.getLong("maxProgressMs"));
        assertEquals(0L, saved.getLong("currentProgressMs"));
        assertEquals(0, saved.getInteger("heartbeatCount"));
        assertEquals("auto", saved.getString("resolution"));
        assertEquals("PLAYING", saved.getString("exitType"));
        assertEquals(saved.getDate("startTime"), saved.getDate("createTime"));
        assertEquals(saved.getDate("createTime"), saved.getDate("updateTime"));
        assertFalse(saved.containsKey("userId"));
        assertFalse(saved.containsKey("endTime"));
        assertFalse(saved.containsKey("_class"));
    }

    @Test
    void authenticatedStartUsesTokenUserAndAnonymousCanUpdateSameSession() throws Exception {
        User user = new User();
        user.setId("fixture-session-user");
        user.setToken("fixture-session-token");
        mongoTemplate.save(user);
        var response = call("start", "{\"userId\":\"body-user\"}", "application/json", user.getToken());
        String id = response.getJSONObject("data").getString("playbackSessionId");
        assertEquals(user.getId(), session(id).getString("userId"));
        assertEquals(0, call("heartbeat", "{\"playbackSessionId\":\"" + id + "\",\"currentTimeMs\":42}")
                .getIntValue("code"));
        assertEquals(42L, session(id).getLong("currentProgressMs"));
        assertEquals(user.getId(), session(id).getString("userId"));
    }

    @Test
    void heartbeatIgnoresPausedFlagAllowsDurationDecreaseAndNullCurrentClearsField() throws Exception {
        String id = start();
        assertEquals(0, call("heartbeat", "{\"playbackSessionId\":\"" + id + "\",\"currentTimeMs\":50,"
                + "\"totalPlayDurationMs\":60,\"resolution\":\"720p\",\"isPlaying\":false}").getIntValue("code"));
        assertEquals(0, call("heartbeat", "{\"playbackSessionId\":\"" + id + "\",\"currentTimeMs\":-5,"
                + "\"totalPlayDurationMs\":-3,\"resolution\":\"\"}").getIntValue("code"));
        assertEquals(50L, session(id).getLong("maxProgressMs"));
        assertEquals(-5L, session(id).getLong("currentProgressMs"));
        assertEquals(-3L, session(id).getLong("totalPlayDurationMs"));
        assertEquals("", session(id).getString("resolution"));
        assertEquals(0, call("heartbeat", "{\"playbackSessionId\":\"" + id + "\"}").getIntValue("code"));
        Document saved = session(id);
        assertFalse(saved.containsKey("currentProgressMs"));
        assertEquals(50L, saved.getLong("maxProgressMs"));
        assertEquals(-3L, saved.getLong("totalPlayDurationMs"));
        assertEquals(3, saved.getInteger("heartbeatCount"));
    }

    @Test
    void textPlainExitAcceptsArbitraryTypeAndLaterHeartbeatKeepsEndTime() throws Exception {
        String id = start();
        assertEquals(0, call("exit", "{\"playbackSessionId\":\"" + id + "\",\"currentTimeMs\":80,"
                + "\"exitType\":\"fixture-other\"}", "text/plain", null).getIntValue("code"));
        Document exited = session(id);
        assertNotNull(exited.getDate("endTime"));
        assertEquals(0, exited.getInteger("heartbeatCount"));
        assertEquals("fixture-other", exited.getString("exitType"));
        assertEquals(0, call("heartbeat", "{\"playbackSessionId\":\"" + id + "\",\"currentTimeMs\":90}")
                .getIntValue("code"));
        Document later = session(id);
        assertEquals(exited.getDate("endTime"), later.getDate("endTime"));
        assertEquals("fixture-other", later.getString("exitType"));
        assertEquals(90L, later.getLong("maxProgressMs"));
        assertEquals(1, later.getInteger("heartbeatCount"));
    }

    @Test
    void unknownSessionIsSuccessButMissingIdIsGenericError() throws Exception {
        for (String route : new String[]{"heartbeat", "exit"}) {
            assertEquals(0, call(route, "{\"playbackSessionId\":\"fixture-missing\"}").getIntValue("code"));
            assertEquals(1, call(route, "{}").getIntValue("code"));
        }
        assertEquals(0, mongoTemplate.getCollection("playbackSession").countDocuments());
    }

    @Test
    void missingCounterFailsWithoutSavingAndIntCounterOverflowWraps() throws Exception {
        String id = start();
        var collection = mongoTemplate.getCollection("playbackSession");
        Document query = new Document("_id", new ObjectId(id));
        collection.updateOne(query, new Document("$unset", new Document("heartbeatCount", "")));
        Document before = session(id);
        assertEquals(1, call("heartbeat", "{\"playbackSessionId\":\"" + id + "\",\"currentTimeMs\":90}")
                .getIntValue("code"));
        assertEquals(before, session(id));
        collection.updateOne(query, new Document("$set", new Document("heartbeatCount", Integer.MAX_VALUE)));
        assertEquals(0, call("heartbeat", "{\"playbackSessionId\":\"" + id + "\"}").getIntValue("code"));
        assertEquals(Integer.MIN_VALUE, session(id).getInteger("heartbeatCount"));
    }
}
