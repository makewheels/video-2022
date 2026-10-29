package com.github.makewheels.video2022.subscription;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.BaseIntegrationTest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Synthetic, read-only business fixtures through the real legacy HTTP and Mongo mapping. */
class SubscriptionReadHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;
    private static final String HEX = "0123456789abcdef01234567";

    private void seed() {
        cleanDatabase();
        mongoTemplate.getCollection("user").insertOne(new Document("_id", "fixture-viewer")
                .append("token", "fixture-subscription-token").append("subscriberCount", 3));
        List<Document> rows = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            rows.add(new Document("_id", "fixture-sub-" + i).append("userId", "fixture-viewer")
                    .append("channelUserId", "channel-" + i).append("createTime", new Date(1000 + i)));
        }
        rows.add(new Document("_id", "fixture-null").append("userId", "fixture-viewer")
                .append("channelUserId", null).append("createTime", new Date(2000)));
        rows.add(new Document("_id", "fixture-missing").append("userId", "fixture-viewer").append("createTime", new Date(2001)));
        rows.add(new Document("_id", "fixture-number").append("userId", "fixture-viewer")
                .append("channelUserId", 7).append("createTime", new Date(2002)));
        rows.add(new Document("_id", "fixture-object").append("userId", "fixture-viewer")
                .append("channelUserId", new ObjectId(HEX)).append("createTime", new Date(2003)));
        rows.add(new Document("_id", "fixture-other").append("userId", "fixture-other")
                .append("channelUserId", "other-channel").append("createTime", new Date(3000)));
        mongoTemplate.getCollection("subscription").insertMany(rows);
    }

    private JSONObject state() {
        JSONObject state = new JSONObject(true);
        for (String name : List.of("user", "subscription")) {
            state.put(name, mongoTemplate.getCollection(name).find().sort(new Document("_id", 1)).into(new ArrayList<>()));
        }
        return state;
    }

    private Object normalized(Object value) {
        if (value instanceof Date date) return date.getTime();
        if (value instanceof ObjectId id) return Map.of("$oid", id.toHexString());
        if (value instanceof List<?> values) return values.stream().map(this::normalized).toList();
        if (value instanceof Map<?, ?> values) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            values.forEach((key, item) -> mapped.put(key.toString(), normalized(item)));
            return mapped;
        }
        return value;
    }

    private List<List<String>> parameters(String operation, String scenario) {
        List<List<String>> result = new ArrayList<>();
        if (operation.equals("getStatus")) {
            String channel = switch (scenario) {
                case "missing-channel" -> "missing-channel";
                case "empty-channel" -> "";
                case "numeric-reference" -> "7";
                case "object-reference" -> HEX;
                case "other-user" -> "other-channel";
                default -> "channel-0";
            };
            if (!scenario.equals("missing-parameter")) result.add(List.of("channelUserId", channel));
            if (scenario.equals("duplicate-parameter")) result.add(List.of("channelUserId", "channel-1"));
        } else {
            switch (scenario) {
                case "page" -> { result.add(List.of("skip", "2")); result.add(List.of("limit", "3")); }
                case "cap" -> result.add(List.of("limit", "500"));
                case "zero" -> result.add(List.of("limit", "0"));
                case "negative-limit" -> result.add(List.of("limit", "-3"));
                case "negative-skip" -> result.add(List.of("skip", "-1"));
                case "past-end" -> result.add(List.of("skip", "1000"));
                case "blank" -> { result.add(List.of("skip", "")); result.add(List.of("limit", "")); }
                case "invalid" -> result.add(List.of("skip", "not-a-number"));
                case "overflow" -> result.add(List.of("limit", "2147483648"));
                case "hex" -> { result.add(List.of("skip", "0x2")); result.add(List.of("limit", " 2 ")); }
                case "duplicate-pagination" -> { result.add(List.of("skip", "0")); result.add(List.of("skip", "2")); }
                default -> { }
            }
        }
        if (scenario.equals("query-token")) result.add(List.of("token", "fixture-subscription-token"));
        return result;
    }

    @Test
    void protectedStatusAndPaginationMatchOldHttpWithoutBusinessWrites() throws Exception {
        JSONObject contract = new JSONObject(true);
        JSONArray cases = new JSONArray();
        try {
            for (String operation : List.of("getStatus", "getMySubscriptions")) {
                List<String> scenarios = operation.equals("getStatus")
                        ? List.of("subscribed", "missing-channel", "empty-channel", "missing-parameter",
                        "duplicate-parameter", "numeric-reference", "object-reference", "other-user",
                        "no-token", "bad-token", "query-token")
                        : List.of("default", "page", "cap", "zero", "negative-limit", "negative-skip",
                        "past-end", "blank", "invalid", "overflow", "hex", "duplicate-pagination",
                        "no-token", "bad-token", "query-token");
                for (String scenario : scenarios) {
                    seed();
                    var before = state();
                    contract.put("before", normalized(before));
                    var request = get("/subscription/" + operation);
                    var parameters = parameters(operation, scenario);
                    Map<String, String> headers = new LinkedHashMap<>();
                    if (!List.of("no-token", "query-token").contains(scenario)) {
                        headers.put("token", scenario.equals("bad-token") ? "invalid" : "fixture-subscription-token");
                    }
                    parameters.forEach(pair -> request.param(pair.get(0), pair.get(1)));
                    headers.forEach(request::header);
                    var response = MockMvcBuilders.webAppContextSetup(context).build().perform(request).andReturn().getResponse();
                    JSONObject row = new JSONObject(true);
                    row.put("operation", operation); row.put("scenario", scenario);
                    row.put("parameters", parameters); row.put("headers", headers);
                    row.put("status", response.getStatus()); row.put("contentType", response.getContentType());
                    row.put("response", JSON.parseObject(response.getContentAsString()));
                    row.put("requestLogs", mongoTemplate.getCollection("requestLog").countDocuments());
                    row.put("exceptionLogs", mongoTemplate.getCollection("exceptionLog").countDocuments());
                    cases.add(row);
                    assertEquals(before, state());
                    verifyNoInteractions(ossVideoService, aliyunMpsService, cloudFunctionTranscodeService, md5CfService);
                }
            }
            contract.put("cases", cases);
            contract.put("after", normalized(state()));
            String serialized = JSON.toJSONString(contract, SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat);
            String output = System.getProperty("contract.subscription-read.output.dir");
            if (output != null) {
                Files.createDirectories(Path.of(output)); Files.writeString(Path.of(output, "cases.json"), serialized);
            } else {
                assertEquals(JSON.parseObject(Files.readString(Path.of("../../serverless/contracts/subscription-read/cases.json"))),
                        JSON.parseObject(serialized));
            }
        } finally {
            cleanDatabase();
        }
    }

    @Test
    void headOptionsAndUnsupportedMethodsKeepOldProtectionAndJournalOrder() throws Exception {
        try {
            seed();
            var before = state();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            for (String operation : List.of("getStatus", "getMySubscriptions")) {
                assertEquals(401, mvc.perform(get("/subscription/" + operation)).andReturn().getResponse().getStatus());
                assertEquals(200, mvc.perform(head("/subscription/" + operation).param("channelUserId", "channel-0")
                        .header("token", "fixture-subscription-token")).andReturn().getResponse().getStatus());
                assertEquals(401, mvc.perform(options("/subscription/" + operation)).andReturn().getResponse().getStatus());
                var allowed = mvc.perform(options("/subscription/" + operation).header("token", "fixture-subscription-token"))
                        .andReturn().getResponse();
                assertEquals(200, allowed.getStatus());
                assertEquals("GET,HEAD,OPTIONS", allowed.getHeader("Allow"));
                var unsupported = mvc.perform(post("/subscription/" + operation)).andReturn().getResponse();
                assertEquals(200, unsupported.getStatus());
                assertEquals(1, JSON.parseObject(unsupported.getContentAsString()).getIntValue("code"));
            }
            assertEquals(4, mongoTemplate.getCollection("requestLog").countDocuments());
            assertEquals(2, mongoTemplate.getCollection("exceptionLog").countDocuments());
            assertEquals(before, state());
        } finally {
            cleanDatabase();
        }
    }
}
