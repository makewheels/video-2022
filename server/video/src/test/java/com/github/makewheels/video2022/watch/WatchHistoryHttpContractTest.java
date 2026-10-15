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

import java.time.Duration;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Only synthetic records in the dedicated test Mongo; never production history. */
class WatchHistoryHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        User user = new User();
        user.setId("fixture-history-user");
        user.setToken("fixture-history-token");
        mongoTemplate.save(user);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void log(String id, String viewer, String video, long time) {
        Document document = new Document("_id", id).append("videoId", video).append("createTime", new Date(time));
        if (viewer != null) document.append("viewerId", viewer);
        mongoTemplate.getCollection("watchLog").insertOne(document);
    }

    private JSONObject call(MockHttpServletRequestBuilder request) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("application/json", response.getContentType());
        return JSON.parseObject(response.getContentAsString());
    }

    @Test
    void duplicateRowsAndDeletedVideoAreRetainedAndCoverUsesTwoHours() throws Exception {
        log("first", "fixture-history-user", "video", 3000);
        log("second", "fixture-history-user", "video", 2000);
        log("third", "fixture-history-user", "deleted", 1000);
        log("other", "other", "video", 4000);
        mongoTemplate.getCollection("video").insertOne(new Document("_id", "video")
                .append("title", "fixture-private-title").append("visibility", "PRIVATE").append("coverId", "cover"));
        mongoTemplate.getCollection("cover").insertOne(new Document("_id", "cover").append("key", "fixture/cover.jpg"));
        when(ossVideoService.generatePresignedUrl("fixture/cover.jpg", Duration.ofHours(2)))
                .thenReturn("https://example.invalid/fixture-cover");
        var body = call(get("/watchHistory/getMyHistory").header("token", "fixture-history-token"));
        assertEquals(0, body.getIntValue("code"));
        var data = body.getJSONObject("data");
        assertEquals(3L, data.getLongValue("total"));
        assertEquals(0, data.getIntValue("page"));
        assertEquals(20, data.getIntValue("pageSize"));
        var rows = data.getJSONArray("list");
        assertEquals(3, rows.size());
        assertEquals("video", rows.getJSONObject(0).getString("videoId"));
        assertEquals("video", rows.getJSONObject(1).getString("videoId"));
        assertEquals("fixture-private-title", rows.getJSONObject(0).getString("title"));
        assertEquals("https://example.invalid/fixture-cover", rows.getJSONObject(0).getString("coverUrl"));
        assertEquals("deleted", rows.getJSONObject(2).getString("videoId"));
        assertNull(rows.getJSONObject(2).get("title"));
        assertNull(rows.getJSONObject(2).get("coverUrl"));
        verify(ossVideoService, times(2)).generatePresignedUrl("fixture/cover.jpg", Duration.ofHours(2));
    }

    @Test
    void anonymousHistoryIncludesMissingAndExplicitNullViewerAndClearPreservesNamedUsers() throws Exception {
        log("missing", null, "one", 1000);
        mongoTemplate.getCollection("watchLog").insertOne(new Document("_id", "null")
                .append("viewerId", null).append("videoId", "two").append("createTime", new Date(2000)));
        log("named", "fixture-history-user", "three", 3000);
        var body = call(get("/watchHistory/getMyHistory"));
        assertEquals(0, body.getIntValue("code"));
        assertEquals(2L, body.getJSONObject("data").getLongValue("total"));
        assertEquals(0, call(delete("/watchHistory/clear")).getIntValue("code"));
        assertEquals(1, mongoTemplate.getCollection("watchLog").countDocuments());
        assertEquals("named", mongoTemplate.getCollection("watchLog").find().first().getString("_id"));
    }

    @Test
    void paginationRetainsOldZeroNegativeAndOverflowBehavior() throws Exception {
        for (int i = 0; i < 3; i++) log("row-" + i, null, "v-" + i, i * 1000L);
        for (String size : new String[]{"0", "-1"}) {
            var data = call(get("/watchHistory/getMyHistory").param("pageSize", size)).getJSONObject("data");
            assertEquals(3, data.getJSONArray("list").size());
            assertEquals(Integer.parseInt(size), data.getIntValue("pageSize"));
        }
        var negative = call(get("/watchHistory/getMyHistory").param("page", "-1").param("pageSize", "2"));
        assertEquals(2, negative.getJSONObject("data").getJSONArray("list").size());
        var overflow = call(get("/watchHistory/getMyHistory").param("page", "2147483647").param("pageSize", "2"));
        assertEquals(2, overflow.getJSONObject("data").getJSONArray("list").size());
        var page = call(get("/watchHistory/getMyHistory").param("page", "1").param("pageSize", "2"));
        assertEquals("v-0", page.getJSONObject("data").getJSONArray("list").getJSONObject(0).getString("videoId"));
        assertEquals(1, call(get("/watchHistory/getMyHistory").param("page", "bad")).getIntValue("code"));
    }

    @Test
    void authenticatedClearLeavesAnonymousAndOtherViewerRows() throws Exception {
        log("mine", "fixture-history-user", "one", 1000);
        log("other", "other", "two", 2000);
        log("anonymous", null, "three", 3000);
        assertEquals(0, call(delete("/watchHistory/clear").header("token", "fixture-history-token"))
                .getIntValue("code"));
        assertEquals(2, mongoTemplate.getCollection("watchLog").countDocuments());
        assertNull(mongoTemplate.getCollection("watchLog").find(new Document("_id", "mine")).first());
    }
    @Test
    void hexVideoAndCoverLookupDoNotFallBackToSameTextStringIds() throws Exception {
        String id = "64b000000000000000000001";
        log("log", null, id, 1000);
        mongoTemplate.getCollection("video").insertOne(new Document("_id", id).append("title", "wrong-string"));
        var first = call(get("/watchHistory/getMyHistory")).getJSONObject("data").getJSONArray("list");
        assertNull(first.getJSONObject(0).get("title"));
        mongoTemplate.getCollection("video").insertOne(new Document("_id", new org.bson.types.ObjectId(id))
                .append("title", "matched").append("coverId", id));
        mongoTemplate.getCollection("cover").insertOne(new Document("_id", id).append("key", "wrong-key"));
        var second = call(get("/watchHistory/getMyHistory")).getJSONObject("data").getJSONArray("list");
        assertEquals("matched", second.getJSONObject(0).getString("title"));
        assertNull(second.getJSONObject(0).get("coverUrl"));
        verifyNoInteractions(ossVideoService);
    }

    private void fixture(String name, JSONObject body) throws Exception {
        String output = System.getProperty("contract.output.dir");
        if (output == null) return;
        java.nio.file.Path directory = java.nio.file.Path.of(output);
        java.nio.file.Files.createDirectories(directory);
        JSONObject envelope = new JSONObject();
        envelope.put("status", 200);
        envelope.put("contentType", "application/json");
        envelope.put("body", body);
        java.nio.file.Files.writeString(directory.resolve(name + ".json"),
                JSON.toJSONString(envelope, com.alibaba.fastjson.serializer.SerializerFeature.WriteMapNullValue));
    }

    @Test
    void stableFullProgressAndHistoryResponses() throws Exception {
        mongoTemplate.getCollection("progress").insertOne(new Document("_id", "fixture-progress")
                .append("videoId", "fixture-video").append("clientId", "fixture-client")
                .append("progressInMillis", 42L).append("createTime", new Date(0)).append("updateTime", new Date(1000)));
        var progress = call(get("/progress/getProgress").param("videoId", "fixture-video")
                .param("clientId", "fixture-client"));
        assertEquals(JSON.parseObject("{\"code\":0,\"message\":\"success\",\"data\":{"
                + "\"id\":\"fixture-progress\",\"videoId\":\"fixture-video\",\"viewerId\":null,"
                + "\"clientId\":\"fixture-client\",\"sessionId\":null,\"progressInMillis\":42,"
                + "\"createTime\":\"1970-01-01T00:00:00.000Z\",\"updateTime\":\"1970-01-01T00:00:01.000Z\"}}"), progress);
        fixture("progress-present", progress);
        var missing = call(get("/progress/getProgress").param("videoId", "absent").param("clientId", "fixture-client"));
        assertEquals(JSON.parseObject("{\"code\":0,\"message\":\"success\",\"data\":null}"), missing);
        fixture("progress-missing", missing);
        log("fixture-log", null, "fixture-video", 1000);
        mongoTemplate.getCollection("video").insertOne(new Document("_id", "fixture-video").append("title", "fixture-title"));
        var history = call(get("/watchHistory/getMyHistory"));
        assertEquals(JSON.parseObject("{\"code\":0,\"message\":\"success\",\"data\":{"
                + "\"list\":[{\"videoId\":\"fixture-video\",\"title\":\"fixture-title\",\"coverUrl\":null,"
                + "\"watchTime\":\"1970-01-01T00:00:01.000Z\"}],\"total\":1,\"page\":0,\"pageSize\":20}}"), history);
        fixture("history-present", history);
        var clear = call(delete("/watchHistory/clear"));
        assertEquals(missing, clear);
        fixture("history-clear", clear);
    }

}
