package com.github.makewheels.video2022.video.like;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real legacy HTTP and complete BSON; identities and all document contents are synthetic. */
class VideoLikeHttpContractTest extends BaseIntegrationTest {
    private static final String HEX_ID = "0123456789abcdef01234567";
    @Autowired private WebApplicationContext context;

    private String videoId(String scenario) {
        return switch (scenario) {
            case "object-id" -> HEX_ID.toUpperCase(Locale.ROOT);
            case "string-hex-id" -> HEX_ID;
            case "empty-parameter" -> "";
            default -> "fixture-video";
        };
    }

    private void seed(String operation, String scenario) {
        cleanDatabase();
        mongoTemplate.getCollection("video_like").createIndex(Indexes.compoundIndex(
                Indexes.ascending("videoId"), Indexes.ascending("userId")),
                new IndexOptions().unique(true).name("video_user_idx"));
        mongoTemplate.getCollection("user").insertOne(new Document("_id", "fixture-viewer")
                .append("token", "fixture-reaction-token").append("phone", "00000000000"));
        String target = operation.equals("dislike") ? LikeType.DISLIKE : LikeType.LIKE;
        String opposite = target.equals(LikeType.LIKE) ? LikeType.DISLIKE : LikeType.LIKE;
        Document video = new Document("_id", scenario.equals("object-id") ? new ObjectId(HEX_ID) : videoId(scenario))
                .append("uploaderId", "fixture-other-owner").append("likeCount", 3).append("dislikeCount", 2)
                .append("visibility", scenario.equals("private-video") ? "PRIVATE" : "PUBLIC")
                .append("deleted", scenario.equals("private-video")).append("extra", "fixture-video-field");
        switch (scenario) {
            case "missing-counts" -> { video.remove("likeCount"); video.remove("dislikeCount"); }
            case "null-counts" -> { video.put("likeCount", null); video.put("dislikeCount", null); }
            case "null-old-count" -> video.put(opposite.equals(LikeType.LIKE) ? "likeCount" : "dislikeCount", null);
            case "null-new-count" -> video.put(target.equals(LikeType.LIKE) ? "likeCount" : "dislikeCount", null);
            case "numeric-counts" -> { video.put("likeCount", 12.75); video.put("dislikeCount", "+7"); }
            case "invalid-counts" -> video.put("likeCount", "not-a-number");
            case "overflow-counts" -> video.put("likeCount", 2147483648L);
            default -> { }
        }
        if (!scenario.equals("missing-video") && !scenario.equals("empty-parameter")) {
            mongoTemplate.getCollection("video").insertOne(video);
        }
        if (List.of("same", "opposite", "other-user", "null-type", "unknown-type",
                "null-old-count", "null-new-count", "object-id").contains(scenario)) {
            String type = switch (scenario) {
                case "opposite", "null-old-count", "null-new-count" -> opposite;
                case "unknown-type" -> "OTHER";
                case "null-type" -> null;
                default -> target;
            };
            mongoTemplate.getCollection("video_like").insertOne(new Document("_id", "fixture-reaction")
                    .append("videoId", scenario.equals("object-id") ? HEX_ID : videoId(scenario))
                    .append("userId", scenario.equals("other-user") ? "fixture-other-viewer" : "fixture-viewer")
                    .append("type", type).append("createTime", new Date(1000)).append("updateTime", new Date(1001))
                    .append("extra", "fixture-reaction-field"));
        }
    }

    private JSONObject state() {
        JSONObject state = new JSONObject(true);
        for (String name : List.of("video", "video_like")) {
            state.put(name, mongoTemplate.getCollection(name).find().sort(new Document("_id", 1)).into(new ArrayList<>()));
        }
        return state;
    }

    private Object normalized(Object value, Map<String, String> ids) {
        if (value instanceof Date date) return date.getTime() < 10000 ? date.getTime() : "<server-date>";
        if (value instanceof ObjectId id) {
            return ids.containsKey(id.toHexString()) ? ids.get(id.toHexString()) : Map.of("$oid", id.toHexString());
        }
        if (value instanceof List<?> values) return values.stream().map(item -> normalized(item, ids)).toList();
        if (value instanceof Map<?, ?> values) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            values.forEach((key, item) -> mapped.put(key.toString(), normalized(item, ids)));
            return mapped;
        }
        return value;
    }

    @Test
    void toggleStatusBindingIdentityAndPartialWritesMatchOldHttpAndCompleteBson() throws Exception {
        JSONArray cases = new JSONArray();
        try {
            for (String operation : List.of("like", "dislike", "getStatus")) {
                List<String> scenarios = operation.equals("getStatus")
                        ? List.of("new", "same", "opposite", "other-user", "no-token", "bad-token", "query-token",
                        "private-video", "missing-video", "missing-parameter", "missing-counts", "null-counts",
                        "numeric-counts", "invalid-counts", "overflow-counts", "null-type", "object-id", "string-hex-id")
                        : List.of("new", "same", "opposite", "other-user", "private-video", "missing-counts",
                        "null-type", "unknown-type", "null-old-count", "null-new-count", "missing-video",
                        "missing-parameter", "empty-parameter", "no-token", "bad-token", "query-token",
                        "duplicate-parameter", "object-id", "string-hex-id");
                for (String scenario : scenarios) {
                    seed(operation, scenario);
                    MockHttpServletRequestBuilder request = get("/videoLike/" + operation);
                    List<List<String>> parameters = new ArrayList<>();
                    Map<String, String> headers = new LinkedHashMap<>();
                    if (!scenario.equals("missing-parameter")) parameters.add(List.of("videoId", videoId(scenario)));
                    if (scenario.equals("duplicate-parameter")) parameters.add(List.of("videoId", "fixture-other"));
                    if (scenario.equals("query-token")) {
                        parameters.add(List.of("token", "fixture-reaction-token"));
                    } else if (!scenario.equals("no-token")) {
                        headers.put("token", scenario.equals("bad-token") ? "invalid" : "fixture-reaction-token");
                    }
                    parameters.forEach(pair -> request.param(pair.get(0), pair.get(1)));
                    headers.forEach(request::header);
                    JSONObject row = new JSONObject(true);
                    row.put("operation", operation); row.put("scenario", scenario);
                    row.put("parameters", parameters); row.put("headers", headers); row.put("before", state());
                    var response = MockMvcBuilders.webAppContextSetup(context).build().perform(request).andReturn().getResponse();
                    row.put("status", response.getStatus()); row.put("contentType", response.getContentType());
                    row.put("response", JSON.parseObject(response.getContentAsString())); row.put("after", state());
                    row.put("requestLogs", mongoTemplate.getCollection("requestLog").countDocuments());
                    row.put("exceptionLogs", mongoTemplate.getCollection("exceptionLog").countDocuments());
                    Map<String, String> ids = new HashMap<>();
                    for (Document document : mongoTemplate.getCollection("video_like").find()) {
                        if (document.get("_id") instanceof ObjectId id) ids.put(id.toHexString(), "<generated-reaction>");
                    }
                    cases.add(normalized(row, ids));
                    verifyNoInteractions(ossVideoService, aliyunMpsService, cloudFunctionTranscodeService, md5CfService);
                }
            }
            String serialized = JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat);
            String output = System.getProperty("contract.video-reactions.output.dir");
            if (output != null) {
                Files.createDirectories(Path.of(output)); Files.writeString(Path.of(output, "cases.json"), serialized);
            } else {
                assertEquals(JSON.parseArray(Files.readString(Path.of("../../serverless/contracts/video-reactions/cases.json"))),
                        JSON.parseArray(serialized));
            }
        } finally {
            cleanDatabase();
        }
    }

    @Test
    void headStillMutatesAndOnlyWriteOptionsRequireLogin() throws Exception {
        try {
            seed("like", "new");
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            for (String operation : List.of("like", "dislike")) {
                assertEquals(401, mvc.perform(get("/videoLike/" + operation)).andReturn().getResponse().getStatus());
            }
            for (int i = 0; i < 2; i++) {
                assertEquals(200, mvc.perform(head("/videoLike/like").param("videoId", "fixture-video")
                        .header("token", "fixture-reaction-token")).andReturn().getResponse().getStatus());
            }
            assertEquals(3, mongoTemplate.getCollection("video").find().first().getInteger("likeCount"));
            assertEquals(0, mongoTemplate.getCollection("video_like").countDocuments());
            assertEquals(200, mvc.perform(head("/videoLike/getStatus").param("videoId", "fixture-video"))
                    .andReturn().getResponse().getStatus());
            assertEquals(401, mvc.perform(options("/videoLike/like")).andReturn().getResponse().getStatus());
            for (String operation : List.of("like", "getStatus")) {
                var request = options("/videoLike/" + operation);
                if (operation.equals("like")) request.header("token", "fixture-reaction-token");
                var response = mvc.perform(request).andReturn().getResponse();
                assertEquals(200, response.getStatus());
                assertEquals("GET,HEAD,OPTIONS", response.getHeader("Allow"));
            }
            for (String operation : List.of("like", "getStatus")) {
                var response = mvc.perform(post("/videoLike/" + operation)).andReturn().getResponse();
                assertEquals(200, response.getStatus());
                assertEquals(1, JSON.parseObject(response.getContentAsString()).getIntValue("code"));
            }
            assertEquals(5, mongoTemplate.getCollection("requestLog").countDocuments());
            assertEquals(3, mongoTemplate.getCollection("requestLog").countDocuments(new Document("request.method", "HEAD")));
            assertEquals(2, mongoTemplate.getCollection("exceptionLog").countDocuments());
        } finally {
            cleanDatabase();
        }
    }
}
