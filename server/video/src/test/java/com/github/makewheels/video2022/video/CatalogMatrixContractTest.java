package com.github.makewheels.video2022.video;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Source HTTP and complete BSON evidence; all cloud services inherit the isolated mocks. */
@TestPropertySource(properties = "transcode.recovery.enabled=false")
class CatalogMatrixContractTest extends BaseIntegrationTest {
    private static final String PUBLIC = "/video/getPublicVideoList";
    private static final long TIME = 1704164645000L;
    private static final JsonWriterSettings BSON = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();
    private static final List<String> COLLECTIONS = List.of("video", "user", "cover");

    @Autowired
    private WebApplicationContext context;
    private MockMvc mvc;
    private final JSONArray cases = new JSONArray();

    @BeforeEach
    void setUp() {
        cleanDatabase();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Override
    protected void cleanDatabase() {
        assertTrue(mongoTemplate.getDb().getName().contains("test"), "Only an isolated test database is allowed");
        super.cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void cleanupRejectsNonTestDatabaseBeforeDroppingCollections() {
        MongoTemplate original = mongoTemplate;
        MongoTemplate unsafe = mock(MongoTemplate.class);
        MongoDatabase database = mock(MongoDatabase.class);
        when(unsafe.getDb()).thenReturn(database);
        when(database.getName()).thenReturn("guard-production");
        try {
            mongoTemplate = unsafe;
            assertThrows(AssertionError.class, this::tearDown);
            verify(unsafe, never()).getCollectionNames();
        } finally {
            mongoTemplate = original;
        }
    }

    private Document video(String id, String visibility, int offset) {
        return new Document("_id", id).append("visibility", visibility)
                .append("title", "fixture .* [x] (a)+? ^$ | \\E \uD83D\uDE00 e\u0301 \u00a0")
                .append("description", "MixedCase\nline").append("tags", List.of("标签", "é"))
                .append("category", "音乐").append("uploaderId", "fixture-uploader")
                .append("createTime", new Date(TIME + offset * 1000L))
                .append("legacyUnknown", new Document("long", 7L).append("null", null));
    }

    private List<Document> seed() {
        List<Document> result = new ArrayList<>();
        result.add(video("fixture-a", "PUBLIC", 0));
        result.add(video("fixture-b", "PUBLIC", 1).append("title", "OTHER Case")
                .append("category", "科技").append("tags", null));
        result.add(video("fixture-private", "PRIVATE", 2));
        result.add(video("fixture-unlisted", "UNLISTED", 3));
        return result;
    }

    private JSONObject snapshot() {
        JSONObject result = new JSONObject(true);
        for (String name : COLLECTIONS) {
            JSONArray documents = new JSONArray();
            for (Document document : mongoTemplate.getCollection(name).find().sort(Sorts.ascending("_id"))) {
                documents.add(JSON.parseObject(document.toJson(BSON), Feature.OrderedField));
            }
            result.put(name, documents);
        }
        return result;
    }

    private void normalizeDates(JSONObject body, long start, long end) {
        JSONObject data = body.getJSONObject("data");
        if (data == null) {
            return;
        }
        JSONArray rows = data.getJSONArray(data.containsKey("list") ? "list" : "content");
        if (rows == null) {
            return;
        }
        for (Object value : rows) {
            JSONObject row = (JSONObject) value;
            String iso = row.getString("createTime");
            if (iso != null) {
                long epoch = Instant.parse(iso).toEpochMilli();
                assertEquals(DateUtil.formatDateTime(new Date(epoch)), row.getString("createTimeString"));
                if (epoch >= start && epoch <= end) {
                    row.put("createTime", "<constructor-date>");
                    row.put("createTimeString", "<local:constructor-date>");
                } else {
                    row.put("createTimeString", "<local:" + epoch + ">");
                }
            }
            String publish = row.getString("youtubePublishTimeString");
            if (publish != null) {
                assertEquals(DateUtil.formatDateTime(new Date(TIME - 1000)), publish);
                row.put("youtubePublishTimeString", "<local:" + (TIME - 1000) + ">");
            }
        }
    }

    private void record(String id, String route, List<Document> videos, String... parameters) throws Exception {
        for (String name : List.of("video", "user", "cover", "requestLog", "exceptionLog")) {
            mongoTemplate.getCollection(name).deleteMany(new Document());
        }
        if (!videos.isEmpty()) {
            mongoTemplate.getCollection("video").insertMany(videos);
        }
        mongoTemplate.getCollection("user").insertOne(new Document("_id", "fixture-uploader")
                .append("phone", "00000000000").append("token", "fixture-old-token")
                .append("nickname", "").append("avatarUrl", "https://example.invalid/avatar")
                .append("unknown", new Document("keep", true)));
        JSONObject before = snapshot();
        MockHttpServletRequestBuilder request = get(route);
        JSONArray pairs = new JSONArray();
        for (int index = 0; index < parameters.length; index += 2) {
            request.param(parameters[index], parameters[index + 1]);
            pairs.add(List.of(parameters[index], parameters[index + 1]));
        }
        long start = System.currentTimeMillis();
        MvcResult result = mvc.perform(request).andReturn();
        long end = System.currentTimeMillis();
        JSONObject body = JSON.parseObject(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(200, result.getResponse().getStatus(), id);
        if (!"/category/list".equals(route)) {
            normalizeDates(body, start, end);
        }
        JSONObject after = snapshot();
        assertEquals(before, after, id + " must remain read-only");
        JSONObject entry = new JSONObject(true);
        entry.put("id", id);
        entry.put("path", route);
        entry.put("parameters", pairs);
        entry.put("before", before);
        entry.put("after", after);
        entry.put("status", result.getResponse().getStatus());
        entry.put("contentType", result.getResponse().getContentType());
        entry.put("body", body);
        entry.put("requestLogs", mongoTemplate.getCollection("requestLog").countDocuments());
        entry.put("exceptionLogs", mongoTemplate.getCollection("exceptionLog").countDocuments());
        cases.add(entry);
    }

    private void field(String id, String name, Object value) throws Exception {
        mutation(id, document -> document.put(name, value));
    }

    private void mutation(String id, Consumer<Document> change) throws Exception {
        List<Document> videos = seed();
        change.accept(videos.get(0));
        record("bson-" + id, PUBLIC, videos, "skip", "0", "limit", "20");
    }

    @Test
    void remainingCatalogHttpAndBsonMatrix() throws Exception {
        record("public-defaults", PUBLIC, seed(), "skip", "0", "limit", "20");
        record("search-defaults", "/search", seed());
        record("categories", "/category/list", seed());
        String[] keywords = {".*", "[", "(", "+", "^$", "\\E", "\\", "\n", " \t ",
                "\u00a0", "\u2003", "标签", "case", "é", "e\u0301", "\uD83D\uDE00", "\0", "fixture\0x"};
        for (int index = 0; index < keywords.length; index++) {
            record("literal-public-" + index, PUBLIC, seed(), "skip", "0", "limit", "20",
                    "keyword", keywords[index]);
            record("literal-search-" + index, "/search", seed(), "q", keywords[index]);
        }
        String[][] pages = {{"0", "1"}, {"1", "1"}, {"2", "1"}, {"-1", "1"}, {"0", "0"},
                {"0", "-1"}, {"0", "-2147483648"}, {"2147483647", "2"}, {"2147483647", "2147483647"},
                {"-2147483648", "-2147483648"}, {"0x1", "#2"}, {"1 2", "2"}};
        for (int index = 0; index < pages.length; index++) {
            record("page-public-" + index, PUBLIC, seed(), "skip", pages[index][0], "limit", pages[index][1]);
            record("page-search-" + index, "/search", seed(), "page", pages[index][0], "pageSize", pages[index][1]);
        }
        String[] categories = {"", " ", "\t\n", "音乐", " 音乐 ", "科技", "unknown", "\u00a0", "\u2003"};
        for (int index = 0; index < categories.length; index++) {
            record("category-" + index, "/search", seed(), "category", categories[index]);
        }
        record("repeat-keyword", PUBLIC, seed(), "skip", "0", "limit", "20",
                "keyword", "fixture", "keyword", "OTHER");
        record("repeat-q", "/search", seed(), "q", "fixture", "q", "OTHER");
        record("repeat-category", "/search", seed(), "category", "音乐", "category", "科技");
        record("repeat-page", "/search", seed(), "page", "0", "page", "1", "pageSize", "1");
        record("empty-search-zero-size", "/search", List.of(), "pageSize", "0");
        record("empty-public-zero-size", PUBLIC, List.of(), "skip", "0", "limit", "0");
        record("missing-public-skip", PUBLIC, seed(), "limit", "20");
        record("empty-search-defaults", "/search", seed(), "page", "", "pageSize", "");
        for (String bad : List.of("wrong", "2147483648", "\u00a01", "1.0")) {
            record("invalid-page-" + cases.size(), "/search", seed(), "page", bad);
        }
        record("old-token", PUBLIC, seed(), "skip", "0", "limit", "20", "token", "fixture-old-token");
        field("title-null", "title", null);
        field("title-int", "title", 17);
        field("title-long", "title", 2147483648L);
        field("title-double", "title", 12.5);
        field("title-scientific", "title", 1.0E20);
        field("title-min-double", "title", Double.MIN_VALUE);
        field("title-negative-subnormal", "title", -2 * Double.MIN_VALUE);
        field("title-nan", "title", Double.NaN);
        field("title-boolean", "title", true);
        field("title-object-id", "title", new ObjectId("0123456789abcdef01234567"));
        field("title-list", "title", List.of("first", "second"));
        field("title-empty-list", "title", List.of());
        field("title-nested-list", "title", Arrays.asList(17, null, new Document("k", "值")));
        field("title-object", "title", new Document("first", 1).append("second", "x"));
        field("title-bson-object", "title", new Document("中文", "\uD83D\uDE00")
                .append("date", new Date(TIME + 123)).append("id", new ObjectId("0123456789abcdef01234567"))
                .append("long", 2147483648L).append("double", 1.0E20).append("tiny", Double.MIN_VALUE)
                .append("negativeZero", -0.0).append("nan", Double.NaN).append("null", null));
        field("description-false", "description", false);
        mutation("tags-missing", document -> document.remove("tags"));
        field("tags-null", "tags", null);
        field("tags-string", "tags", "first,second");
        field("tags-spaced-string", "tags", " \tfirst ,\u00a0second\u00a0,, ");
        field("tags-empty-string", "tags", "");
        field("tags-number", "tags", 17);
        field("tags-boolean", "tags", true);
        field("tags-mixed", "tags", Arrays.asList("first", null, 17));
        field("tags-empty", "tags", List.of());
        field("tags-object", "tags", new Document());
        for (String name : List.of("watch", "mediaInfo", "youTube")) {
            field(name + "-null", name, null);
            field(name + "-empty-list", name, List.of());
            field(name + "-zero", name, 0);
            field(name + "-empty-string", name, "");
            field(name + "-object", name, new Document());
        }
        field("watch-count-null", "watch", new Document("watchCount", null));
        field("watch-count-overflow", "watch", new Document("watchCount", 2147483648L));
        field("watch-count-double", "watch", new Document("watchCount", 12.75));
        field("watch-count-string", "watch", new Document("watchCount", "17"));
        mutation("date-missing", document -> document.remove("createTime"));
        field("date-null", "createTime", null);
        field("date-long", "createTime", TIME);
        field("date-string", "createTime", "2024-01-02T03:04:05.000Z");
        field("status-null", "status", null);
        for (Object visibility : Arrays.asList(null, "public", "OTHER", true, List.of("PUBLIC", "PRIVATE"))) {
            field("visibility-" + cases.size(), "visibility", visibility);
        }
        mutation("youtube", document -> {
            document.put("videoType", "YOUTUBE");
            document.put("youTube", new Document("publishTime", new Date(TIME - 1000)));
        });
        field("unknown-video-type", "videoType", "UNKNOWN");
        field("category-list", "category", List.of("音乐", "科技"));
        mutation("object-id", document -> document.put("_id", new ObjectId("0123456789abcdef01234567")));
        String text = JSON.toJSONString(cases, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue);
        String output = System.getProperty("contract.catalog.output.dir");
        if (output != null) {
            Path directory = Path.of(output);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("catalog.json"), text, StandardCharsets.UTF_8);
        } else {
            Path fixed = Path.of("..", "..", "serverless", "contracts", "catalog-matrix", "catalog.json");
            JSONArray expected = JSON.parseArray(Files.readString(fixed, StandardCharsets.UTF_8));
            JSONArray actual = JSON.parseArray(text);
            assertEquals(expected.size(), actual.size());
            for (int index = 0; index < actual.size(); index++) {
                assertEquals(expected.get(index), actual.get(index), actual.getJSONObject(index).getString("id"));
            }
        }
    }
}
