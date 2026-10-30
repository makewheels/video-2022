package com.github.makewheels.video2022.notification;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real legacy HTTP/BSON contracts; all media/network services remain mocked. */
class InteractionHttpContractTest extends BaseIntegrationTest {
    private static final String HEX = "0123456789abcdef01234567";
    private static final String TOKEN = "fixture-interaction-token";
    @Autowired private WebApplicationContext context;

    private void users() {
        mongoTemplate.getCollection("user").insertMany(List.of(
                new Document("_id", "fixture-viewer").append("token", TOKEN).append("subscriberCount", 3),
                new Document("_id", "fixture-channel").append("subscriberCount", 5).append("untouched", "keep")));
    }

    private void seedSubscription(String operation, String scenario) {
        cleanDatabase();
        users();
        boolean unsubscribe = operation.equals("unsubscribe");
        Object channelId = scenario.equals("object-channel") ? new ObjectId(HEX)
                : scenario.equals("string-hex-channel") ? HEX : "fixture-channel";
        if (!channelId.equals("fixture-channel")) {
            mongoTemplate.getCollection("user").deleteOne(new Document("_id", "fixture-channel"));
            mongoTemplate.getCollection("user").insertOne(new Document("_id", channelId)
                    .append("subscriberCount", 5).append("untouched", "keep"));
        }
        if (scenario.equals("channel-missing")) {
            mongoTemplate.getCollection("user").deleteOne(new Document("_id", channelId));
        } else if (scenario.equals("null-count")) {
            mongoTemplate.getCollection("user").updateOne(new Document("_id", channelId),
                    new Document("$set", new Document("subscriberCount", null)));
        } else if (scenario.equals("missing-count")) {
            mongoTemplate.getCollection("user").updateOne(new Document("_id", channelId),
                    new Document("$unset", new Document("subscriberCount", "")));
        } else if (scenario.equals("counter-overflow")) {
            mongoTemplate.getCollection("user").updateOne(new Document("_id", channelId),
                    new Document("$set", new Document("subscriberCount", Integer.MAX_VALUE)));
        } else if (scenario.equals("long-count")) {
            mongoTemplate.getCollection("user").updateOne(new Document("_id", channelId),
                    new Document("$set", new Document("subscriberCount", 1L << 40)));
        }
        mongoTemplate.getCollection("subscription").createIndex(
                new Document("userId", 1).append("channelUserId", 1),
                new IndexOptions().unique(true).name("uk_user_channel"));
        if ((unsubscribe && !scenario.equals("not-subscribed")) || scenario.equals("existing")) {
            mongoTemplate.getCollection("subscription").insertOne(new Document(
                    "_id", scenario.equals("object-channel") ? new ObjectId(HEX) : "fixture-subscription")
                    .append("userId", "fixture-viewer")
                    .append("channelUserId", channelId instanceof ObjectId ? HEX : channelId)
                    .append("createTime", new Date(1000)).append("untouched", "keep"));
        }
    }

    private void seedNotifications(String scenario) {
        cleanDatabase();
        users();
        List<Document> rows = new ArrayList<>();
        rows.add(new Document("_id", "fixture-unread").append("toUserId", "fixture-viewer")
                .append("fromUserId", "fixture-channel").append("type", "VIDEO_LIKE")
                .append("content", "fixture content").append("relatedVideoId", "fixture-video")
                .append("relatedCommentId", null).append("read", false).append("createTime", new Date(1000))
                .append("untouched", "keep"));
        rows.add(new Document("_id", "fixture-read").append("toUserId", "fixture-viewer")
                .append("read", true).append("createTime", new Date(2000)));
        rows.add(new Document("_id", "fixture-missing").append("toUserId", "fixture-viewer")
                .append("content", 7).append("read", false).append("createTime", new Date(3000)));
        rows.add(new Document("_id", "fixture-null").append("toUserId", "fixture-viewer")
                .append("read", false).append("createTime", new Date(4000)));
        rows.add(new Document("_id", new ObjectId(HEX)).append("toUserId", "fixture-viewer")
                .append("read", false).append("createTime", new Date(5000)));
        rows.add(new Document("_id", HEX).append("toUserId", "fixture-viewer")
                .append("read", false).append("createTime", new Date(6000)));
        rows.add(new Document("_id", "fixture-other").append("toUserId", "fixture-other")
                .append("read", false).append("createTime", new Date(7000)));
        rows.add(new Document("_id", "7").append("toUserId", "fixture-viewer")
                .append("read", false).append("createTime", new Date(8000)));
        rows.add(new Document("_id", 7).append("toUserId", "fixture-viewer")
                .append("read", false).append("createTime", new Date(9000)));
        mongoTemplate.getCollection("notification").insertMany(rows);
        if (scenario.equals("cap")) {
            for (int i = 0; i < 60; i++) {
                mongoTemplate.getCollection("notification").insertOne(new Document("_id", "fixture-extra-" + i)
                        .append("toUserId", "fixture-viewer").append("read", false).append("createTime", new Date(100 + i)));
            }
        } else if (List.of("empty", "zero-empty").contains(scenario)) {
            mongoTemplate.getCollection("notification").deleteMany(new Document("toUserId", "fixture-viewer"));
        } else if (scenario.equals("string-only-hex")) {
            mongoTemplate.getCollection("notification").deleteOne(new Document("_id", new ObjectId(HEX)));
        } else if (scenario.equals("null-target")) {
            mongoTemplate.getCollection("notification").insertOne(new Document("_id", null)
                    .append("toUserId", "fixture-viewer").append("read", false));
        } else if (List.of("read-string", "read-number", "read-empty").contains(scenario)) {
            Object value = scenario.equals("read-number") ? 1 : scenario.equals("read-empty") ? "" : "false";
            mongoTemplate.getCollection("notification").updateOne(new Document("_id", "fixture-unread"),
                    new Document("$set", new Document("read", value)));
        } else if (List.of("missing-read", "mixed-flags").contains(scenario)) {
            mongoTemplate.getCollection("notification").updateOne(new Document("_id", "fixture-missing"),
                    new Document("$unset", new Document("read", "")));
        }
        if (List.of("null-read", "mixed-flags").contains(scenario)) {
            mongoTemplate.getCollection("notification").updateOne(new Document("_id", "fixture-null"),
                    new Document("$set", new Document("read", null)));
        }
        if (List.of("no-token", "bad-token").contains(scenario)) {
            mongoTemplate.getCollection("notification").insertMany(List.of(
                    new Document("_id", "fixture-null-owner").append("toUserId", null)
                            .append("read", false).append("createTime", new Date(100)),
                    new Document("_id", "fixture-missing-owner")
                            .append("read", false).append("createTime", new Date(200))));
        }
        if (scenario.equals("already-read")) {
            mongoTemplate.getCollection("notification").updateMany(new Document("toUserId", "fixture-viewer"),
                    new Document("$set", new Document("read", true)));
        } else if (scenario.equals("write-failure")) {
            mongoTemplate.getDb().runCommand(new Document("collMod", "notification")
                    .append("validator", new Document("read", false)));
        }
    }

    private JSONObject state(String collection) {
        JSONObject result = new JSONObject(true);
        for (String name : List.of("user", collection)) {
            result.put(name, mongoTemplate.getCollection(name).find().sort(new Document("_id", 1)).into(new ArrayList<>()));
        }
        return result;
    }

    private Object normalize(Object value, Map<String, String> generated) {
        if (value instanceof Date date) return date.getTime() < 10000 ? date.getTime() : "<server-date>";
        if (value instanceof ObjectId id) {
            return generated.containsKey(id.toHexString()) ? generated.get(id.toHexString()) : Map.of("$oid", id.toHexString());
        }
        if (value instanceof List<?> values) return values.stream().map(item -> normalize(item, generated)).toList();
        if (value instanceof Map<?, ?> values) {
            Map<String, Object> result = new LinkedHashMap<>();
            values.forEach((name, item) -> result.put(name.toString(), normalize(item, generated)));
            return result;
        }
        return value;
    }

    private JSONObject record(MockMvc mvc, String operation, String scenario, String collection,
                              String method, List<List<String>> parameters, String body) throws Exception {
        JSONObject before = state(collection);
        Set<Object> previousIds = new HashSet<>();
        for (Object row : before.getJSONArray(collection)) previousIds.add(((Document) row).get("_id"));
        String path = "/" + (collection.equals("subscription") ? "subscription" : "notification") + "/" + operation;
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method), path);
        Map<String, String> headers = new LinkedHashMap<>();
        if (!List.of("no-token", "query-token").contains(scenario)) {
            headers.put("token", scenario.equals("bad-token") ? "invalid" : TOKEN);
        }
        if (scenario.equals("query-token")) parameters.add(List.of("token", TOKEN));
        parameters.forEach(pair -> request.param(pair.get(0), pair.get(1)));
        if (body != null) {
            headers.put("Content-Type", "application/json");
            request.content(body);
        }
        headers.forEach(request::header);
        var response = mvc.perform(request).andReturn().getResponse();
        JSONObject after = state(collection);
        Map<String, String> generated = new LinkedHashMap<>();
        for (Object row : after.getJSONArray(collection)) {
            Object id = ((Document) row).get("_id");
            if (id instanceof ObjectId objectId && !previousIds.contains(id)) {
                generated.put(objectId.toHexString(), "<generated-" + collection + ">");
            }
        }
        JSONObject result = new JSONObject(true);
        result.put("operation", operation); result.put("scenario", scenario);
        result.put("method", method); result.put("parameters", parameters); result.put("headers", headers);
        result.put("input", body); result.put("status", response.getStatus()); result.put("contentType", response.getContentType());
        result.put("response", JSON.parseObject(response.getContentAsString()));
        result.put("before", normalize(before, Map.of())); result.put("after", normalize(after, generated));
        result.put("requestLogs", mongoTemplate.getCollection("requestLog").countDocuments());
        result.put("exceptionLogs", mongoTemplate.getCollection("exceptionLog").countDocuments());
        verifyNoInteractions(ossVideoService, ossDataService, aliyunMpsService, cloudFunctionTranscodeService,
                md5CfService, youtubeService);
        return result;
    }

    private void contract(String name, JSONArray cases) throws Exception {
        String serialized = JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat);
        String output = System.getProperty("contract.interactions.output.dir");
        if (output != null) {
            Files.createDirectories(Path.of(output));
            Files.writeString(Path.of(output, name + ".json"), serialized);
        } else {
            assertEquals(JSON.parseArray(Files.readString(
                    Path.of("..", "..", "serverless", "contracts", "interactions", name + ".json"))),
                    JSON.parseArray(serialized));
        }
    }

    @Test
    void subscriptionWritesKeepOldErrorsIdsCountersAndPartialWrites() throws Exception {
        JSONArray cases = new JSONArray();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        try {
            for (String operation : List.of("subscribe", "unsubscribe")) {
                List<String> scenarios = new ArrayList<>(List.of("self", "channel-missing", "blank", "object-channel",
                        "string-hex-channel", "null-count", "missing-count", "counter-overflow", "long-count", "missing-parameter",
                        "duplicate-parameter", "no-token", "bad-token", "query-token"));
                scenarios.addAll(operation.equals("subscribe") ? List.of("new", "existing")
                        : List.of("existing", "not-subscribed"));
                for (String scenario : scenarios) {
                    seedSubscription(operation, scenario);
                    List<List<String>> parameters = new ArrayList<>();
                    String channel = switch (scenario) {
                        case "self" -> "fixture-viewer";
                        case "blank" -> "";
                        case "object-channel", "string-hex-channel" -> HEX;
                        default -> "fixture-channel";
                    };
                    if (!scenario.equals("missing-parameter")) parameters.add(List.of("channelUserId", channel));
                    if (scenario.equals("duplicate-parameter")) parameters.add(List.of("channelUserId", channel));
                    cases.add(record(mvc, operation, scenario, "subscription", "GET", parameters, null));
                    if (scenario.equals("counter-overflow") && operation.equals("subscribe")) {
                        assertInstanceOf(Long.class, mongoTemplate.getCollection("user")
                                .find(new Document("_id", "fixture-channel")).first().get("subscriberCount"));
                    }
                }
            }
            contract("subscription-write", cases);
        } finally {
            cleanDatabase();
        }
    }

    @Test
    void notificationReadsKeepOldPagingNullsAndReadOnlyState() throws Exception {
        JSONArray cases = new JSONArray();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        try {
            for (String operation : List.of("getMyNotifications", "getUnreadCount")) {
                List<String> scenarios = operation.equals("getMyNotifications")
                        ? List.of("default", "page", "cap", "zero", "zero-empty", "negative-size", "negative-page",
                        "overflow-page", "blank", "invalid", "duplicate-pagination",
                        "read-string", "read-number", "read-empty", "missing-read", "null-read",
                        "no-token", "bad-token", "query-token")
                        : List.of("default", "empty", "no-token", "bad-token", "query-token");
                for (String scenario : scenarios) {
                    seedNotifications(scenario);
                    List<List<String>> parameters = new ArrayList<>();
                    switch (scenario) {
                        case "page" -> { parameters.add(List.of("page", "1")); parameters.add(List.of("pageSize", "2")); }
                        case "cap" -> parameters.add(List.of("pageSize", "500"));
                        case "zero", "zero-empty" -> parameters.add(List.of("pageSize", "0"));
                        case "negative-size" -> parameters.add(List.of("pageSize", "-2"));
                        case "negative-page" -> parameters.add(List.of("page", "-1"));
                        case "overflow-page" -> {
                            parameters.add(List.of("page", "2147483647")); parameters.add(List.of("pageSize", "2"));
                        }
                        case "blank" -> { parameters.add(List.of("page", "")); parameters.add(List.of("pageSize", "")); }
                        case "invalid" -> parameters.add(List.of("pageSize", "2147483648"));
                        case "duplicate-pagination" -> {
                            parameters.add(List.of("page", "0")); parameters.add(List.of("page", "2"));
                        }
                        default -> { }
                    }
                    JSONObject row = record(mvc, operation, scenario, "notification", "GET", parameters, null);
                    assertEquals(row.get("before"), row.get("after"));
                    cases.add(row);
                }
            }
            contract("notification-read", cases);
        } finally {
            cleanDatabase();
        }
    }

    private String notificationBody(String scenario) {
        return switch (scenario) {
            case "other" -> "{\"notificationId\":\"fixture-other\"}";
            case "object-id", "string-only-hex" -> "{\"notificationId\":\"" + HEX + "\"}";
            case "numeric" -> "{\"notificationId\":7}";
            case "boolean" -> "{\"notificationId\":true}";
            case "array" -> "{\"notificationId\":[1,null,\"x\"]}";
            case "object" -> "{\"notificationId\":{\"a\":1,\"b\":null}}";
            case "missing-field", "null-target" -> "{}";
            case "null-field" -> "{\"notificationId\":null}";
            case "duplicate-field" -> "{\"notificationId\":[],\"notificationId\":\"fixture-unread\"}";
            case "null-root" -> "null";
            case "bad-json" -> "{";
            case "missing-body" -> null;
            default -> "{\"notificationId\":\"fixture-unread\"}";
        };
    }

    @Test
    void notificationReadStateKeepsBindingOwnerAndNarrowWrites() throws Exception {
        JSONArray cases = new JSONArray();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        try {
            for (String operation : List.of("markAsRead", "markAllAsRead")) {
                List<String> scenarios = operation.equals("markAsRead")
                        ? List.of("owned", "other", "object-id", "string-only-hex", "numeric", "boolean", "array", "object",
                        "missing-field", "null-field", "null-target", "duplicate-field", "null-root", "bad-json",
                        "missing-body", "no-token", "bad-token", "query-token", "write-failure")
                        : List.of("default", "mixed-flags", "already-read", "no-token", "bad-token", "query-token", "write-failure",
                        "bad-json", "missing-body");
                for (String scenario : scenarios) {
                    seedNotifications(scenario);
                    cases.add(record(mvc, operation, scenario, "notification", "POST", new ArrayList<>(),
                            notificationBody(scenario)));
                }
            }
            contract("notification-write", cases);
        } finally {
            cleanDatabase();
        }
    }

    @Test
    void methodProtectionHeadMutationsAndOptionsMatchOldMvc() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        try {
            for (String operation : List.of("subscribe", "unsubscribe", "getMyNotifications", "getUnreadCount",
                    "markAsRead", "markAllAsRead")) {
                boolean subscription = operation.equals("subscribe") || operation.equals("unsubscribe");
                boolean write = operation.startsWith("mark");
                if (subscription) seedSubscription(operation, "default"); else seedNotifications("default");
                String path = "/" + (subscription ? "subscription" : "notification") + "/" + operation;
                assertEquals(subscription ? 401 : 200, mvc.perform(request(write ? HttpMethod.POST : HttpMethod.GET, path))
                        .andReturn().getResponse().getStatus());
                assertEquals(subscription ? 401 : 200,
                        mvc.perform(options(path)).andReturn().getResponse().getStatus());
                var allowed = mvc.perform(options(path).header("token", TOKEN)).andReturn().getResponse();
                assertEquals(200, allowed.getStatus());
                assertEquals(write ? "POST,OPTIONS" : "GET,HEAD,OPTIONS", allowed.getHeader("Allow"));
                var unsupported = mvc.perform(put(path)).andReturn().getResponse();
                assertEquals(200, unsupported.getStatus());
                assertEquals(1, JSON.parseObject(unsupported.getContentAsString()).getIntValue("code"));
                if (!write) {
                    assertEquals(200, mvc.perform(head(path).param("channelUserId", "fixture-channel")
                            .header("token", TOKEN)).andReturn().getResponse().getStatus());
                    if (subscription) {
                        assertEquals(operation.equals("subscribe") ? 1 : 0,
                                mongoTemplate.getCollection("subscription").countDocuments());
                    }
                }
            }
        } finally {
            cleanDatabase();
        }
    }
}
