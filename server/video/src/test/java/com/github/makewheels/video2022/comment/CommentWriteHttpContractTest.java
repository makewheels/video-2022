package com.github.makewheels.video2022.comment;

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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real old comment writes with real generated ObjectIds; all content and identities are synthetic. */
class CommentWriteHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;

    private void seed(String scenario) {
        cleanDatabase();
        mongoTemplate.getCollection("comment_like").createIndex(Indexes.compoundIndex(
                Indexes.ascending("commentId"), Indexes.ascending("userId")),
                new IndexOptions().unique(true).name("comment_user_idx"));
        mongoTemplate.getCollection("user").insertOne(new Document("_id", "fixture-viewer")
                .append("token", "fixture-comment-token").append("phone", "00000000000")
                .append("nickname", "fixture viewer").append("avatarUrl", "https://example.invalid/avatar"));
        if (!scenario.equals("missing-video") && !scenario.equals("author-missing-video")) {
            mongoTemplate.getCollection("video").insertOne(new Document("_id", "fixture-video")
                    .append("uploaderId", scenario.equals("forbidden") || scenario.equals("author") ? "fixture-owner"
                            : scenario.equals("null-uploader") ? null : "fixture-viewer").append("commentCount", 2));
        }
        if (!scenario.equals("missing-comment")) {
            mongoTemplate.getCollection("comment").insertOne(new Document("_id", "fixture-parent")
                    .append("videoId", scenario.equals("cross-video") ? "other-video" : "fixture-video")
                    .append("userId", scenario.equals("author") || scenario.equals("author-missing-video") ? "fixture-viewer"
                            : scenario.equals("null-author") ? null : "fixture-author")
                    .append("userPhone", "00000000000").append("userNickname", "fixture parent")
                    .append("content", "fixture parent").append("likeCount", 3).append("replyCount", 1)
                    .append("createTime", new Date(1000)).append("updateTime", new Date(1001)));
            mongoTemplate.getCollection("comment").insertOne(new Document("_id", "fixture-child")
                    .append("videoId", "fixture-video").append("parentId", "fixture-parent")
                    .append("userId", "fixture-child-author").append("userPhone", "00000000000")
                    .append("userNickname", "fixture child").append("content", "fixture child")
                    .append("createTime", new Date(2000)).append("updateTime", new Date(2001)));
        }
        if (scenario.equals("toggle-off") || scenario.equals("cascade-likes")) {
            mongoTemplate.getCollection("comment_like").insertOne(new Document("_id", "fixture-like-parent")
                    .append("commentId", "fixture-parent").append("userId", "fixture-viewer").append("createTime", new Date(3000)));
            mongoTemplate.getCollection("comment_like").insertOne(new Document("_id", "fixture-like-child")
                    .append("commentId", "fixture-child").append("userId", "fixture-viewer").append("createTime", new Date(3001)));
        }
    }

    private JSONObject state() {
        JSONObject state = new JSONObject(true);
        for (String name : List.of("video", "comment", "comment_like")) {
            state.put(name, mongoTemplate.getCollection(name).find().sort(new Document("_id", 1)).into(new ArrayList<>()));
        }
        return state;
    }

    private Object normalized(Object value, Map<String, String> ids) {
        if (value instanceof Date date) return date.getTime() < 10000 ? date.getTime() : "<server-date>";
        if (value instanceof ObjectId id) return ids.getOrDefault(id.toHexString(), id.toHexString());
        if (value instanceof String text) return ids.getOrDefault(text, text);
        if (value instanceof List<?> values) return values.stream().map(item -> normalized(item, ids)).toList();
        if (value instanceof Map<?, ?> values) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            values.forEach((key, item) -> {
                Object normal = normalized(item, ids);
                if ((key.equals("createTime") || key.equals("updateTime")) && item instanceof String text
                        && !text.startsWith("1970-")) normal = "<server-date>";
                mapped.put(key.toString(), normal);
            });
            return mapped;
        }
        return value;
    }

    private String body(String scenario) {
        JSONObject body = new JSONObject(true);
        body.put("videoId", "fixture-video");
        body.put("content", "  fixture new comment  ");
        switch (scenario) {
            case "reply", "cross-video" -> body.put("parentId", "fixture-parent");
            case "reply-child" -> body.put("parentId", "fixture-child");
            case "missing-parent" -> body.put("parentId", "fixture-missing");
            case "empty-parent" -> body.put("parentId", "");
            case "blank-content" -> body.put("content", " \t\r\n ");
            case "null-content" -> body.put("content", null);
            case "empty-content" -> body.put("content", "");
            case "long-content" -> body.put("content", "x".repeat(2001));
            case "max-content" -> body.put("content", "x".repeat(2000));
            case "emoji-limit" -> body.put("content", "😀".repeat(1000));
            case "emoji-over" -> body.put("content", "😀".repeat(1001));
            case "numeric-content" -> body.put("content", 123);
            case "boolean-content" -> body.put("content", true);
            case "array-content" -> body.put("content", List.of(1, true, "fixture"));
            case "object-content" -> body.put("content", JSON.parseObject("{\"text\":null,\"n\":1}"));
            default -> { }
        }
        return switch (scenario) {
            case "empty-body" -> "";
            case "null-body" -> "null";
            case "array-body" -> "[]";
            case "duplicate-content" -> "{\"videoId\":\"fixture-video\",\"content\":{},\"content\":\"fixture\"}";
            case "exponent-content" -> "{\"videoId\":\"fixture-video\",\"content\":1e3}";
            default -> JSON.toJSONString(body, SerializerFeature.WriteMapNullValue);
        };
    }

    @Test
    void headMutatesLikeGetAndOptionsRemainsProtectedWithoutUnsupportedMethodJournal() throws Exception {
        try {
            seed("success");
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            for (int i = 0; i < 2; i++) {
                assertEquals(200, mvc.perform(head("/comment/like").param("commentId", "fixture-parent")
                        .header("token", "fixture-comment-token")).andReturn().getResponse().getStatus());
            }
            assertEquals(3, mongoTemplate.getCollection("comment").find(new Document("_id", "fixture-parent"))
                    .first().getInteger("likeCount"));
            assertEquals(0, mongoTemplate.getCollection("comment_like").countDocuments());
            assertEquals(401, mvc.perform(options("/comment/like")).andReturn().getResponse().getStatus());
            var allow = mvc.perform(options("/comment/like").header("token", "fixture-comment-token"))
                    .andReturn().getResponse();
            assertEquals(200, allow.getStatus());
            assertEquals("GET,HEAD,OPTIONS", allow.getHeader("Allow"));
            var unsupported = mvc.perform(post("/comment/like")).andReturn().getResponse();
            assertEquals(200, unsupported.getStatus());
            assertEquals(1, JSON.parseObject(unsupported.getContentAsString()).getIntValue("code"));
            assertEquals(1, JSON.parseObject(mvc.perform(get("/comment/add")).andReturn()
                    .getResponse().getContentAsString()).getIntValue("code"));
            assertEquals(200, mvc.perform(head("/comment/delete").param("commentId", "fixture-parent")
                    .header("token", "fixture-comment-token")).andReturn().getResponse().getStatus());
            assertEquals(0, mongoTemplate.getCollection("comment").countDocuments());
            assertEquals(0, mongoTemplate.getCollection("video").find().first().getInteger("commentCount"));
            assertEquals(4, mongoTemplate.getCollection("requestLog").countDocuments());
            assertEquals(3, mongoTemplate.getCollection("requestLog").countDocuments(new Document("request.method", "HEAD")));
            assertEquals(2, mongoTemplate.getCollection("exceptionLog").countDocuments());
        } finally {
            cleanDatabase();
        }
    }

    @Test
    void addRepliesDeleteCascadeAndLikeToggleMatchOldHttpAndCompleteBson() throws Exception {
        JSONArray cases = new JSONArray();
        try {
            for (String operation : List.of("add", "delete", "like")) {
                List<String> scenarios = operation.equals("add")
                        ? List.of("success", "reply", "reply-child", "cross-video", "missing-parent", "empty-parent",
                        "blank-content", "null-content", "empty-content", "long-content", "max-content", "emoji-limit",
                        "emoji-over", "numeric-content", "boolean-content", "array-content", "object-content",
                        "duplicate-content", "exponent-content", "missing-video", "empty-body", "null-body", "array-body",
                        "no-token", "bad-token")
                        : operation.equals("delete")
                        ? List.of("success", "child", "cascade-likes", "forbidden", "author", "missing-video",
                        "author-missing-video", "null-author", "null-uploader", "missing-comment", "missing-parameter", "no-token")
                        : List.of("success", "toggle-off", "missing-comment", "missing-video", "missing-parameter", "no-token");
                for (String scenario : scenarios) {
                    seed(scenario);
                    String input = body(scenario);
                    var request = operation.equals("add") ? post("/comment/add").contentType("application/json").content(input)
                            : get("/comment/" + operation);
                    String commentId = scenario.equals("child") ? "fixture-child" : "fixture-parent";
                    if (!operation.equals("add") && !scenario.equals("missing-parameter")) request.param("commentId", commentId);
                    if (!scenario.equals("no-token")) request.header("token", scenario.equals("bad-token") ? "invalid" : "fixture-comment-token");
                    JSONObject row = new JSONObject(true);
                    row.put("operation", operation); row.put("scenario", scenario); row.put("input", input);
                    row.put("commentId", commentId); row.put("before", state());
                    var response = MockMvcBuilders.webAppContextSetup(context).build().perform(request).andReturn().getResponse();
                    row.put("status", response.getStatus()); row.put("contentType", response.getContentType());
                    row.put("response", JSON.parseObject(response.getContentAsString())); row.put("after", state());
                    row.put("requestLogs", mongoTemplate.getCollection("requestLog").countDocuments());
                    row.put("exceptionLogs", mongoTemplate.getCollection("exceptionLog").countDocuments());
                    Map<String, String> ids = new HashMap<>();
                    for (String collection : List.of("comment", "comment_like")) {
                        for (Document document : mongoTemplate.getCollection(collection).find()) {
                            if (document.get("_id") instanceof ObjectId id) ids.put(id.toHexString(), "<generated-" + collection + ">");
                        }
                    }
                    cases.add(normalized(row, ids));
                    verifyNoInteractions(ossVideoService, aliyunMpsService, cloudFunctionTranscodeService, md5CfService);
                }
            }
            String serialized = JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat);
            String output = System.getProperty("contract.comment-write.output.dir");
            if (output != null) {
                Files.createDirectories(Path.of(output)); Files.writeString(Path.of(output, "cases.json"), serialized);
            } else {
                assertEquals(JSON.parseArray(Files.readString(Path.of("../../serverless/contracts/comment-write/cases.json"))),
                        JSON.parseArray(serialized));
            }
        } finally {
            cleanDatabase();
        }
    }
}
