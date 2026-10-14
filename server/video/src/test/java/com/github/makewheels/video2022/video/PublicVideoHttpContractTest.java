package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Date;
import java.util.List;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Actual Java HTTP baseline on isolated Mongo; no production endpoint is called. */
class PublicVideoHttpContractTest extends BaseIntegrationTest {
    private static final String ROUTE = "/video/getPublicVideoList";
    private static final long FIXED_TIME = 1704164645000L;

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

    private Video video(String id, String visibility, String title, long time) {
        Video value = new Video();
        value.setId(id);
        value.setTitle(title);
        value.setVisibility(visibility);
        value.setCreateTime(new Date(time));
        value.setUploaderId("fixture-uploader");
        mongoTemplate.save(value);
        return value;
    }

    private MockHttpServletRequestBuilder request() {
        return get(ROUTE).param("skip", "0").param("limit", "20");
    }

    private JSONObject body(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentType().startsWith("application/json"));
        return JSON.parseObject(result.getResponse().getContentAsString());
    }

    @Test
    void anonymousPageKeepsPublicNonReadyAndLogsRequest() throws Exception {
        video("fixture-old", "PUBLIC", "old", FIXED_TIME);
        video("fixture-new", "PUBLIC", "new", FIXED_TIME + 1000);
        video("fixture-private", "PRIVATE", "private", FIXED_TIME + 2000);
        video("fixture-unlisted", "UNLISTED", "unlisted", FIXED_TIME + 3000);
        JSONObject response = body(get(ROUTE).param("skip", "1").param("limit", "1"));
        assertEquals(0, response.getIntValue("code"));
        assertEquals("success", response.getString("message"));
        JSONObject data = response.getJSONObject("data");
        assertEquals(2, data.getLongValue("total"));
        JSONArray rows = data.getJSONArray("list");
        assertEquals(1, rows.size());
        assertEquals("fixture-old", rows.getJSONObject(0).getString("id"));
        assertEquals("CREATED", rows.getJSONObject(0).getString("status"));
        assertEquals(1, mongoTemplate.getCollection("requestLog").countDocuments());
    }

    @Test
    void keywordIsLiteralTrimmedAndCaseInsensitiveAcrossFields() throws Exception {
        Video literal = video("fixture-literal", "PUBLIC", "literal .*", FIXED_TIME);
        video("fixture-other", "PUBLIC", "anything", FIXED_TIME + 1000);
        JSONObject literalData = body(request().param("keyword", "  .*  ")).getJSONObject("data");
        assertEquals(1, literalData.getLongValue("total"));
        assertEquals(literal.getId(), literalData.getJSONArray("list").getJSONObject(0).getString("id"));
        literal.setDescription("MixedCase");
        literal.setTags(List.of("标签"));
        mongoTemplate.save(literal);
        for (String keyword : List.of("mixedcase", "标签")) {
            assertEquals(1, body(request().param("keyword", keyword)).getJSONObject("data").getLongValue("total"));
        }
        assertEquals(2, body(request().param("keyword", "   ")).getJSONObject("data").getLongValue("total"));
    }

    @Test
    void missingAndInvalidPaginationUseExistingErrorEnvelope() throws Exception {
        for (MockHttpServletRequestBuilder invalid : List.of(
                get(ROUTE).param("limit", "20"),
                get(ROUTE).param("skip", "0"),
                get(ROUTE).param("skip", "wrong").param("limit", "20"),
                get(ROUTE).param("skip", "2147483648").param("limit", "20"))) {
            JSONObject response = body(invalid);
            assertEquals(1, response.getIntValue("code"));
            assertEquals("未知错误", response.getString("message"));
            assertTrue(response.containsKey("data"));
            assertNull(response.get("data"));
        }
        assertEquals(4, mongoTemplate.getCollection("exceptionLog").countDocuments());
    }

    @Test
    void zeroLimitIsUnboundedAndEmptyPageKeepsTotal() throws Exception {
        video("fixture-one", "PUBLIC", "one", FIXED_TIME);
        video("fixture-two", "PUBLIC", "two", FIXED_TIME + 1000);
        JSONObject unlimited = body(get(ROUTE).param("skip", "0").param("limit", "0")).getJSONObject("data");
        assertEquals(2, unlimited.getJSONArray("list").size());
        JSONObject negative = body(get(ROUTE).param("skip", "-1").param("limit", "1")).getJSONObject("data");
        assertEquals(1, negative.getJSONArray("list").size());
        assertEquals("fixture-two", negative.getJSONArray("list").getJSONObject(0).getString("id"));
        JSONObject empty = body(get(ROUTE).param("skip", "2").param("limit", "1")).getJSONObject("data");
        assertEquals(0, empty.getJSONArray("list").size());
        assertEquals(2, empty.getLongValue("total"));
    }

    @Test
    void oldTokenNullsAndHistoricalLinksArePreserved() throws Exception {
        User user = new User();
        user.setId("fixture-uploader");
        user.setToken("fixture-old-token");
        user.setPhone("13800000000");
        mongoTemplate.save(user);
        Video value = video("fixture-video", "PUBLIC", "fixture", FIXED_TIME);
        value.setMediaInfo(null);
        value.getWatch().setWatchId("fixture-watch");
        value.getWatch().setWatchUrl("https://example.invalid/watch/fixture-watch");
        value.getWatch().setShortUrl("https://example.invalid/s/fixture");
        mongoTemplate.save(value);
        JSONObject row = body(request().header("token", "fixture-old-token"))
                .getJSONObject("data").getJSONArray("list").getJSONObject(0);
        assertEquals("138****0000", row.getString("uploaderName"));
        assertEquals("https://example.invalid/watch/fixture-watch", row.getString("watchUrl"));
        assertEquals("https://example.invalid/s/fixture", row.getString("shortUrl"));
        for (String field : List.of("coverUrl", "description", "type", "userId", "duration", "uploaderAvatarUrl")) {
            assertTrue(row.containsKey(field), field);
            assertNull(row.get(field), field);
        }
        String expectedDisplay = Instant.ofEpochMilli(FIXED_TIME).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertEquals(expectedDisplay, row.getString("createTimeString"));
        assertEquals("2024-01-02T03:04:05.000Z", row.getString("createTime"));
        mongoTemplate.remove(user);
        value.setWatch(null);
        mongoTemplate.save(value);
        JSONObject missing = body(request().param("token", "invalid-fixture-token"))
                .getJSONObject("data").getJSONArray("list").getJSONObject(0);
        assertEquals("未知用户", missing.getString("uploaderName"));
        assertNull(missing.get("watchId"));
        // Mongo reconstruction retains the nested object's constructor default.
        assertEquals(0, missing.getIntValue("watchCount"));
    }
}
