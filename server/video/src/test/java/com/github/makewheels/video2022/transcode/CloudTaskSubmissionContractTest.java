package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.transcode.task.FcTask;
import com.sun.net.httpserver.HttpServer;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CloudTaskSubmissionContractTest extends CloudTaskContractSupport {
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private String cpuUrl;
    private String gpuUrl;

    private HttpServer server() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                requests.incrementAndGet();
                byte[] bytes = exchange.getRequestBody().readAllBytes();
                JSONObject payload = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
                JSONObject call = new JSONObject(true);
                call.put("kind", "function.http"); call.put("path", exchange.getRequestURI().getPath());
                call.put("method", exchange.getRequestMethod()); call.put("payload", payload);
                call.put("invocationType", exchange.getRequestHeaders().getFirst("X-Fc-Invocation-Type"));
                call.put("contentType", exchange.getRequestHeaders().getFirst("Content-Type"));
                call.put("contentLength", exchange.getRequestHeaders().getFirst("Content-Length"));
                call.put("secretMatched", "fixture-invocation-only".equals(
                        exchange.getRequestHeaders().getFirst("X-Invoke-Secret")));
                call.put("atSend", state()); calls.add(call);
                assertEquals(String.valueOf(bytes.length), call.getString("contentLength"));
                assertEquals("Async", call.getString("invocationType"));
                assertTrue(call.getBooleanValue("secretMatched"));
                if ("drop".equals(response.get())) return;
                if ("fast".equals(response.get())) {
                    assertTrue(tasks.finish(payload.getString("taskId"), ATTEMPT, "SUCCEEDED", new Date(), null));
                }
                if (!"no-request-id".equals(response.get())) {
                    exchange.getResponseHeaders().set("X-Fc-Request-Id", "fixture-request");
                }
                int status = switch (response.get()) {
                    case "429", "throttle-last" -> 429;
                    case "400" -> 400;
                    case "500" -> 500;
                    case "200" -> 200;
                    case "204" -> 204;
                    case "redirect" -> 302;
                    default -> 202;
                };
                if (status == 302) exchange.getResponseHeaders().set("Location", "/paid-again");
                exchange.sendResponseHeaders(status, -1);
            } catch (IOException | RuntimeException | AssertionError failure) {
                serverFailure.set(failure);
                throw failure;
            } finally {
                exchange.close();
            }
        });
        server.start();
        cpuUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/cpu";
        gpuUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/gpu";
        return server;
    }

    private void validator(Document rule) {
        if (!mongoTemplate.collectionExists("fcTask")) mongoTemplate.createCollection("fcTask");
        mongoTemplate.getDb().runCommand(new Document("collMod", "fcTask").append("validator", rule));
    }

    private JSONObject record(String provider, String operation, String scenario) {
        startCase(provider);
        requests.set(0); serverFailure.set(null); response.set(scenario);
        ReflectionTestUtils.setField(client, "cpuFunctionUrl", cpuUrl);
        ReflectionTestUtils.setField(client, "gpuFunctionUrl", gpuUrl);
        FcTask task = task(operation, provider);
        JSONObject payload = task.getPayloadSnapshot();
        if ("COVER".equals(operation)) payload.put("inputKey", "fixture/raw/\u539f\u7247.mp4");
        if (scenario.startsWith("existing-")) store(task, scenario.substring("existing-".length()));
        switch (scenario) {
            case "old-attempt" -> {
                store(task, "CREATED");
                mongoTemplate.getCollection("fcTask").updateOne(new Document("_id", task.getId()),
                        new Document("$set", new Document("attemptId", "other-attempt")));
            }
            case "throttle-last" -> { task.setSubmitFailures(29); store(task, "CREATED"); }
            case "default-timeout" -> task.setExecutionTimeoutMs(null);
            case "null-deadline" -> { task.setExecutionTimeoutMs(null); task.setDeadline(null); }
            case "zero-timeout" -> task.setExecutionTimeoutMs(0L);
            case "output-base-absent" -> task.setOutputBaseDir(null);
            case "output-all-absent" -> { task.setOutputBaseDir(null); payload.remove("outputDir"); }
            case "bad-media-key" -> payload.put("playlistKey", "playlist.m3u8");
            case "unconfigured" -> ReflectionTestUtils.setField(client, "gpuFunctionUrl", "");
            case "unknown-provider" -> task.setProvider("UNSUPPORTED");
            case "missing-lane" -> mongoTemplate.getCollection("transcode").deleteOne(new Document("_id", task.getRefId()));
            case "claim-write-failure" -> validator(new Document("status", "CREATED"));
            case "receipt-write-failure" -> validator(new Document("fcRequestId", new Document("$exists", false)));
            default -> { }
        }
        JSONObject row = begin(provider + "-" + operation + "-" + scenario);
        row.put("operation", operation); row.put("provider", task.getProvider());
        row.put("input", JSON.parseObject(JSON.toJSONString(payload, SerializerFeature.WriteMapNullValue)));
        row.put("returned", null); row.put("errorClass", null);
        try {
            row.put("returned", submitter.submit(task, payload));
        } catch (RuntimeException failure) {
            row.put("errorClass", failure.getClass().getName());
        }
        row.put("payloadAfter", payload); row.put("requests", requests.get());
        assertNull(serverFailure.get(), "Loopback handler must not fail");
        boolean noSend = scenario.startsWith("existing-") && !"existing-CREATED".equals(scenario)
                || List.of("old-attempt", "bad-media-key", "unconfigured",
                "unknown-provider", "claim-write-failure").contains(scenario);
        assertEquals(noSend ? 0 : 1, requests.get(), "No redirect or uncertain paid POST replay");
        if (requests.get() == 1) {
            assertEquals(CPU.equals(provider) ? "/cpu" : "/gpu", calls.get(0).getString("path"));
            Document storedAtSend = (Document) calls.get(0).getJSONObject("atSend").getJSONArray("fcTask").get(0);
            assertEquals("SUBMITTED", storedAtSend.getString("status"));
        }
        return end(row);
    }

    @Test
    void realSubmitterAndLoopbackClientMatchTypedTaskBsonAndPaidCallOrder() throws Exception {
        HttpServer server = server();
        try {
            JSONArray cases = new JSONArray();
            for (String provider : List.of(CPU, GPU)) {
                for (String operation : List.of("PROBE", "TRANSCODE", "COVER")) {
                    for (String scenario : List.of("202", "429", "400", "500", "drop", "redirect")) {
                        cases.add(record(provider, operation, scenario));
                    }
                }
            }
            for (String scenario : List.of("200", "204", "no-request-id", "throttle-last",
                    "existing-CREATED", "existing-SUBMITTED", "existing-PROCESSING", "existing-RETRY_WAIT",
                    "existing-FALLBACK_PENDING", "existing-SUCCEEDED", "existing-FAILED", "old-attempt",
                    "default-timeout", "null-deadline", "zero-timeout", "output-base-absent",
                    "output-all-absent", "bad-media-key", "unconfigured", "unknown-provider",
                    "missing-lane", "fast", "claim-write-failure", "receipt-write-failure")) {
                cases.add(record(GPU, "TRANSCODE", scenario));
            }
            contract("submission", cases);
        } finally {
            server.stop(0);
            cleanDatabase();
        }
    }
}
