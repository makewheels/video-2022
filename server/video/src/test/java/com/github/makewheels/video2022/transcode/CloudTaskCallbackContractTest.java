package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.aliyun.oss.model.OSSObject;
import com.github.makewheels.video2022.transcode.task.FcTask;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class CloudTaskCallbackContractTest extends CloudTaskContractSupport {
    private JSONObject manifest(FcTask task) {
        JSONObject result = new JSONObject(true);
        result.put("schemaVersion", 1); result.put("taskId", task.getId()); result.put("attemptId", ATTEMPT);
        result.put("operation", task.getOperation()); result.put("playlistKey", DIR + "/playlist.m3u8");
        result.put("imageKey", DIR + "/cover.jpg"); result.put("width", 1280); result.put("height", 720);
        result.put("dynamicRange", "SDR"); result.put("frameRate", "30/1");
        return result;
    }

    private void objects(String scenario, String text) {
        when(files.getObject(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            JSONObject call = new JSONObject(true);
            call.put("kind", "manifest.get"); call.put("key", key); call.put("atDispatch", state()); calls.add(call);
            if ("object-error".equals(scenario)) throw new IllegalStateException("synthetic object failure");
            if ("object-missing".equals(scenario)) return null;
            OSSObject object = new OSSObject();
            object.setKey(key);
            if (!"stream-missing".equals(scenario)) {
                var content = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)) {
                    @Override public void close() throws IOException {
                        calls.add(new JSONObject(Map.of("kind", "manifest.close", "key", key)));
                        super.close();
                    }
                };
                object.setObjectContent("read-error".equals(scenario) ? new FilterInputStream(content) {
                    @Override public byte[] readAllBytes() throws IOException {
                        throw new IOException("synthetic read failure");
                    }
                } : content);
            }
            return object;
        });
    }

    private JSONObject record(MockMvc mvc, String operation, String scenario) throws Exception {
        String provider = "TRANSCODE".equals(operation) ? GPU : CPU;
        startCase(provider);
        FcTask task = task(operation, provider);
        if ("attempt-limit".equals(scenario)) task.setAttemptCount(2);
        if ("failure-null-error".equals(scenario)) task.setErrorMessage("previous error");
        store(task, scenario.startsWith("task-") ? scenario.substring(5) : "SUBMITTED");
        JSONObject body = new JSONObject(true);
        body.put("taskId", task.getId()); body.put("attemptId", ATTEMPT); body.put("status", "SUCCEEDED");
        body.put("outputManifestKey", task.getPayloadSnapshot().getString("outputDir") + "/result.json");
        JSONObject manifest = manifest(task);
        String raw = null;
        switch (scenario) {
            case "missing-task-id" -> body.remove("taskId");
            case "null-task-id" -> body.put("taskId", null);
            case "missing-attempt" -> body.remove("attemptId");
            case "null-status" -> body.put("status", null);
            case "unknown-task" -> body.put("taskId", "unknown-task");
            case "old-attempt" -> { body.put("attemptId", "old-attempt"); body.put("status", "UNKNOWN"); }
            case "unknown-status" -> body.put("status", "UNKNOWN");
            case "lowercase-status" -> body.put("status", "succeeded");
            case "numeric-status" -> body.put("status", 1);
            case "wrong-path" -> body.put("outputManifestKey", "other/result.json");
            case "path-missing" -> body.remove("outputManifestKey");
            case "provider-ignored" -> { body.put("provider", "ALIYUN_MPS"); body.put("operation", "COVER"); }
            case "schema-string" -> manifest.put("schemaVersion", "1");
            case "schema-decimal" -> manifest.put("schemaVersion", 1.99);
            case "schema-long-wrap" -> manifest.put("schemaVersion", 4294967297L);
            case "schema-null" -> manifest.put("schemaVersion", null);
            case "schema-missing" -> manifest.remove("schemaVersion");
            case "schema-wrong" -> manifest.put("schemaVersion", 2);
            case "manifest-task" -> manifest.put("taskId", "another-task");
            case "manifest-attempt" -> manifest.put("attemptId", "another-attempt");
            case "manifest-operation" -> manifest.put("operation", "OTHER");
            case "manifest-null" -> raw = "null";
            case "manifest-malformed" -> raw = "{";
            case "snapshot-null" -> mongoTemplate.getCollection("fcTask").updateOne(new Document("_id", task.getId()),
                    new Document("$set", new Document("payloadSnapshot", null)));
            case "video-missing" -> mongoTemplate.getCollection("video").deleteOne(new Document("_id", "fixture-video"));
            case "operation-unknown" -> {
                manifest.put("operation", "OTHER");
                mongoTemplate.getCollection("fcTask").updateOne(new Document("_id", task.getId()),
                        new Document("$set", new Document("operation", "OTHER")));
            }
            case "max-null" -> mongoTemplate.getCollection("fcTask").updateOne(new Document("_id", task.getId()),
                    new Document("$set", new Document("maxAttempts", null)));
            case "claim-write-failure" -> mongoTemplate.getDb().runCommand(
                    new Document("collMod", "fcTask").append("validator",
                            new Document("status", new Document("$ne", "PROCESSING"))));
            default -> { }
        }
        if (scenario.startsWith("failure") || List.of("attempt-limit", "max-null", "retry-jitter").contains(scenario)) {
            body.put("status", "FAILED"); body.put("errorMessage", "fixture failure");
            if ("failure-false".equals(scenario)) body.put("retryable", false);
            if ("failure-string-false".equals(scenario)) body.put("retryable", "false");
            if ("failure-zero".equals(scenario)) body.put("retryable", 0);
            if ("failure-null-error".equals(scenario)) body.put("errorMessage", null);
            if ("failure-long-error".equals(scenario)) body.put("errorMessage", "x".repeat(501));
            if ("failure-map-error".equals(scenario)) {
                JSONObject message = new JSONObject(true);
                message.put("z", 7); message.put("a", 1); body.put("errorMessage", message);
            }
            if ("retry-jitter".equals(scenario)) {
                ReflectionTestUtils.setField(callbacks, "retryJitterMs", 3000L); jitter = true;
            }
        }
        objects(scenario, raw == null ? JSON.toJSONString(manifest, SerializerFeature.WriteMapNullValue) : raw);
        if (List.of("registration-error", "registration-programming-error").contains(scenario)) {
            doAnswer(invocation -> {
                hook("register.transcode", invocation.getArgument(0), invocation.getArgument(1));
                IllegalStateException cause = new IllegalStateException("synthetic registration failure");
                if ("registration-error".equals(scenario)) throw new TranscodeCallbackService.RegistrationException(cause);
                throw cause;
            }).when(registrations).onSelfHostedTranscodeFinish(any(), any());
        }
        JSONObject row = begin(operation + "-" + scenario);
        String request = JSON.toJSONString(body, SerializerFeature.WriteMapNullValue);
        if ("bad-json".equals(scenario) || "bad-json-before-secret".equals(scenario)) request = "{";
        if ("json-null".equals(scenario)) request = "null";
        if ("json-array".equals(scenario)) request = "[]";
        row.put("input", request);
        JSONArray responses = new JSONArray();
        int repeats = "duplicate".equals(scenario) || "registration-error".equals(scenario) ? 2 : 1;
        for (int repeat = 0; repeat < repeats; repeat++) {
            String path = "/transcode/cloudFunctionCallback";
            MockHttpServletRequestBuilder builder = switch (scenario) {
                case "get-method" -> get(path);
                case "options-method" -> options(path);
                default -> post(path);
            };
            if (!"secret-missing".equals(scenario)) {
                builder.header("X-Callback-Secret", scenario.startsWith("secret-wrong")
                        || "bad-json-before-secret".equals(scenario) ? "wrong-fixture" : SECRET);
            }
            builder.contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(request);
            var response = mvc.perform(builder).andReturn().getResponse();
            JSONObject result = new JSONObject(true);
            result.put("status", response.getStatus()); result.put("contentType", response.getContentType());
            result.put("allow", response.getHeader("Allow"));
            String text = response.getContentAsString(StandardCharsets.UTF_8);
            result.put("body", text.isEmpty() ? null : JSON.parse(text)); responses.add(result);
        }
        row.put("responses", responses);
        if ("valid".equals(scenario)) {
            assertEquals("SUCCEEDED", tasks.getById(task.getId()).getStatus());
            assertEquals(1, calls.stream().filter(call -> call.getString("kind").startsWith("register.")).count());
        }
        if ("registration-error".equals(scenario)) {
            assertEquals("PROCESSING", tasks.getById(task.getId()).getStatus());
            assertEquals(1, calls.stream().filter(call -> "register.transcode".equals(call.getString("kind"))).count());
        }
        return end(row);
    }

    @Test
    void realCallbackMvcKeepsIdentityManifestLeaseRetryAndLegacyErrorShapes() throws Exception {
        MockMvc mvc = mvc();
        try {
            JSONArray cases = new JSONArray();
            for (String operation : List.of("PROBE", "TRANSCODE", "COVER")) {
                for (String scenario : List.of("valid", "duplicate", "failure", "failure-false", "provider-ignored")) {
                    cases.add(record(mvc, operation, scenario));
                }
            }
            for (String scenario : List.of("secret-missing", "secret-wrong", "missing-task-id", "null-task-id",
                    "missing-attempt", "null-status", "unknown-task", "old-attempt", "unknown-status", "lowercase-status",
                    "numeric-status", "wrong-path", "path-missing", "schema-string", "schema-decimal", "schema-long-wrap",
                    "schema-null", "schema-missing", "schema-wrong", "manifest-task", "manifest-attempt", "manifest-operation",
                    "manifest-null", "manifest-malformed", "object-error", "object-missing", "stream-missing", "read-error",
                    "snapshot-null", "operation-unknown", "registration-error", "registration-programming-error",
                    "task-CREATED", "task-PROCESSING", "task-RETRY_WAIT", "task-FALLBACK_PENDING", "task-SUCCEEDED", "task-FAILED",
                    "failure-string-false", "failure-zero", "failure-null-error", "failure-long-error", "failure-map-error",
                    "attempt-limit", "max-null", "retry-jitter", "claim-write-failure",
                    "bad-json", "bad-json-before-secret", "json-null", "json-array", "get-method", "options-method")) {
                cases.add(record(mvc, "TRANSCODE", scenario));
            }
            cases.add(record(mvc, "PROBE", "video-missing"));
            contract("callback", cases);
        } finally {
            cleanDatabase();
        }
    }
}
