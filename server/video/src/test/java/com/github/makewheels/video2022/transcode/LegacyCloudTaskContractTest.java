package com.github.makewheels.video2022.transcode;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.models.QueryJobListResponse;
import com.aliyun.mts20140618.models.SubmitJobsResponse;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionTranscodeService;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.factory.AliyunCfTranscodeImpl;
import com.github.makewheels.video2022.transcode.factory.TranscodeFactory;
import com.github.makewheels.video2022.transcode.mps.MpsFallbackService;
import com.github.makewheels.video2022.transcode.task.FcTask;
import com.github.makewheels.video2022.video.VideoRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class LegacyCloudTaskContractTest extends CloudTaskContractSupport {
    @Autowired private TranscodeFactory factory;
    @Autowired private TranscodeRepository transcodes;
    @Autowired private VideoRepository videos;
    @Autowired private AliyunCfTranscodeImpl legacyCf;

    private void legacySubmit(boolean failure) {
        EnvironmentService original = (EnvironmentService) ReflectionTestUtils.getField(legacyCf, "environmentService");
        EnvironmentService environment = mock(EnvironmentService.class);
        when(environment.getCallbackUrl(anyString())).thenAnswer(invocation -> "https://example.invalid" + invocation.getArgument(0));
        ReflectionTestUtils.setField(legacyCf, "environmentService", environment);
        when(cloudFunctionTranscodeService.transcode(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            String jobId = invocation.getArgument(4);
            assertTrue(jobId.matches("[0-9]{16,20}")); generated.put(jobId, "<generated-legacy-job>");
            JSONObject call = new JSONObject(true);
            List<String> names = List.of("inputKey", "outputDir", "videoId", "transcodeId", "jobId", "resolution",
                    "width", "height", "videoCodec", "audioCodec", "quality", "callbackUrl");
            call.put("kind", "legacy.cf.submit");
            for (int index = 0; index < names.size(); index++) call.put(names.get(index), invocation.getArgument(index));
            call.put("atSend", state()); calls.add(call);
            if (failure) throw new IllegalStateException("synthetic legacy submit failure");
            return "fixture-legacy-response";
        });
        try {
            legacyCf.transcode(videos.getById("fixture-video"), transcodes.getById("fixture-transcode"));
        } finally {
            ReflectionTestUtils.setField(legacyCf, "environmentService", original);
        }
    }

    private String legacyWire() {
        CloudFunctionTranscodeService service = new CloudFunctionTranscodeService();
        ReflectionTestUtils.setField(service, "bucket", "fixture-bucket");
        ReflectionTestUtils.setField(service, "endpoint", "https://oss.example.invalid");
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        when(request.body(anyString())).thenAnswer(invocation -> {
            calls.add(new JSONObject(Map.of("kind", "legacy.cf.body", "payload", JSON.parseObject(invocation.getArgument(0)))));
            return request;
        });
        when(request.header(anyString(), anyString())).thenAnswer(invocation -> {
            calls.add(new JSONObject(Map.of("kind", "legacy.cf.header", "name", invocation.getArgument(0),
                    "value", invocation.getArgument(1)))); return request;
        });
        when(request.execute()).thenReturn(response);
        when(response.header("X-Fc-Request-Id")).thenReturn("fixture-request");
        when(response.body()).thenReturn("fixture-legacy-response");
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.createPost(anyString())).thenAnswer(invocation -> {
                calls.add(new JSONObject(Map.of("kind", "legacy.cf.url", "url", invocation.getArgument(0))));
                return request;
            });
            return service.transcode("fixture/raw/source.mp4", BASE, "fixture-video", "fixture-transcode",
                    "fixture-job", "720p", 1280, 720, "h264", "aac", "keep", "https://example.invalid/callback");
        }
    }

    private JSONObject recordCf(MockMvc mvc, String scenario) throws Exception {
        startCase(TranscodeProvider.ALIYUN_CLOUD_FUNCTION);
        if ("null-job-on-record".equals(scenario)) mongoTemplate.getCollection("transcode")
                .updateOne(new Document("_id", "fixture-transcode"), new Document("$set", new Document("jobId", null)));
        if ("handler-error".equals(scenario)) {
            doAnswer(invocation -> {
                calls.add(new JSONObject(Map.of("kind", "register.legacy", "atDispatch", state())));
                throw new IllegalStateException("synthetic legacy registration failure");
            }).when(registrations).onTranscodeFinish(any());
        }
        JSONObject row = begin("legacy-cf-" + scenario);
        row.put("errorClass", null);
        try {
            if (scenario.startsWith("submit")) legacySubmit("submit-error".equals(scenario));
            else if ("wire".equals(scenario)) row.put("returned", legacyWire());
            else {
                JSONObject body = new JSONObject(true);
                if (!"missing-job".equals(scenario)) body.put("jobId",
                        "null-job-on-record".equals(scenario) ? null
                                : "unknown-job".equals(scenario) ? "another-job" : "fixture-legacy-job");
                row.put("input", body);
                JSONArray results = new JSONArray();
                for (int repeat = 0; repeat < ("duplicate".equals(scenario) ? 2 : 1); repeat++) {
                    var request = post("/transcode/aliyunCloudFunctionTranscodeCallback")
                            .contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8")
                            .content(JSON.toJSONString(body, com.alibaba.fastjson.serializer.SerializerFeature.WriteMapNullValue));
                    if (!"secret-missing".equals(scenario)) {
                        request.header("X-Callback-Secret", "secret-wrong".equals(scenario) ? "wrong-fixture" : SECRET);
                    }
                    var response = mvc.perform(request).andReturn().getResponse();
                    String text = response.getContentAsString(StandardCharsets.UTF_8);
                    JSONObject result = new JSONObject(true);
                    result.put("status", response.getStatus()); result.put("contentType", response.getContentType());
                    result.put("body", text.isEmpty() ? null : JSON.parse(text)); results.add(result);
                }
                row.put("responses", results);
            }
        } catch (RuntimeException failure) {
            row.put("errorClass", failure.getClass().getName());
        }
        if ("duplicate".equals(scenario)) {
            assertEquals(2, calls.stream().filter(call -> "register.legacy".equals(call.getString("kind"))).count());
        }
        return end(row);
    }

    private void query(String status, boolean failure) {
        fixedDates.put(Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), "<mps-finish>");
        when(aliyunMpsService.queryTranscodeJob(anyString())).thenAnswer(invocation -> {
            calls.add(new JSONObject(Map.of("kind", "mps.query", "jobId", invocation.getArgument(0), "atQuery", state())));
            if (failure) throw new IllegalStateException("synthetic MPS query failure");
            return JSON.parseObject("{\"body\":{\"jobList\":{\"job\":[{\"jobId\":\"fixture-legacy-job\","
                    + "\"state\":\"" + status + "\",\"finishTime\":\"2026-10-01T00:00:00Z\","
                    + "\"output\":{\"properties\":{\"width\":\"1280\",\"height\":\"720\",\"fps\":\"30\","
                    + "\"streams\":{\"audioStreamList\":{\"audioStream\":[]}}}}}]}}}", QueryJobListResponse.class);
        });
    }

    private void mpsSubmit(boolean failure) {
        when(aliyunMpsService.submitTranscodeJobByResolution(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            calls.add(new JSONObject(Map.of("kind", "mps.submit", "inputKey", invocation.getArgument(0),
                    "playlistKey", invocation.getArgument(1), "resolution", invocation.getArgument(2), "atSend", state())));
            if (failure) throw new IllegalStateException("synthetic MPS submit failure");
            return JSON.parseObject("{\"body\":{\"jobResultList\":{\"jobResult\":["
                    + "{\"job\":{\"jobId\":\"fixture-mps-job\",\"state\":\"Submitted\"}}]}}}", SubmitJobsResponse.class);
        });
        CompletableFuture<Void> scheduled = new CompletableFuture<>();
        // Intercept only the legacy loop; Mongo's other CompletableFuture operations stay real.
        try (var futures = mockStatic(CompletableFuture.class, invocation -> {
            if ("runAsync".equals(invocation.getMethod().getName()) && invocation.getArguments().length == 1) {
                calls.add(new JSONObject(Map.of("kind", "mps.legacy-poll-scheduled", "atDispatch", state())));
                return scheduled;
            }
            return invocation.callRealMethod();
        })) {
            factory.getService(TranscodeProvider.ALIYUN_MPS)
                    .transcode(videos.getById("fixture-video"), transcodes.getById("fixture-transcode"));
        }
    }

    private JSONObject recordMps(String scenario) {
        startCase(TranscodeProvider.ALIYUN_MPS);
        if ("same-status".equals(scenario)) mongoTemplate.getCollection("transcode").updateOne(
                new Document("_id", "fixture-transcode"), new Document("$set", new Document("status", "TranscodeSuccess")));
        String status = List.of("TranscodeFail", "TranscodeCancelled", "Transcoding", "Submitted").contains(scenario)
                ? scenario : "TranscodeSuccess";
        query(status, "query-error".equals(scenario));
        JSONObject row = begin("legacy-mps-" + scenario);
        row.put("errorClass", null);
        try {
            if (scenario.startsWith("submit")) mpsSubmit("submit-error".equals(scenario));
            else factory.getService(TranscodeProvider.ALIYUN_MPS)
                    .callback("unknown-job".equals(scenario) ? "unknown-job" : "fixture-legacy-job");
        } catch (RuntimeException failure) {
            row.put("errorClass", failure.getClass().getName());
        }
        return end(row);
    }

    private MpsFallbackService isolatedFallback() {
        MpsFallbackService service = new MpsFallbackService();
        ReflectionTestUtils.setField(service, "aliyunMpsService", aliyunMpsService);
        ReflectionTestUtils.setField(service, "videoRepository", videos);
        ReflectionTestUtils.setField(service, "transcodeRepository", transcodes);
        ReflectionTestUtils.setField(service, "fcTaskRepository", tasks);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongoTemplate);
        ReflectionTestUtils.setField(service, "callbackService", registrations);
        ReflectionTestUtils.setField(service, "recoveryEnabled", true);
        ReflectionTestUtils.setField(service, "fallbackEnabled", true);
        ReflectionTestUtils.setField(service, "fallbackTemplate720p", "fixture-template");
        ReflectionTestUtils.setField(service, "fallbackTemplate1080p", "fixture-template");
        return service;
    }

    private JSONObject recordFallback(String scenario) {
        startCase(GPU);
        MpsFallbackService service = isolatedFallback();
        ReflectionTestUtils.setField(callbacks, "mpsFallbackService", service);
        FcTask task = task("TRANSCODE", GPU);
        task.setAttemptCount(2);
        boolean poll = scenario.startsWith("poll-");
        if (poll && !scenario.endsWith("expired")) {
            Date deadline = new Date(System.currentTimeMillis() + 600_000L);
            fixedDates.put(deadline.getTime(), "<seed-active-deadline>"); task.setDeadline(deadline);
        }
        store(task, poll ? "FALLBACK_PENDING" : "SUBMITTED");
        if (poll) {
            Document fields = new Document("status", "Submitted").append("currentProvider", TranscodeProvider.ALIYUN_MPS)
                    .append("fallbackCount", 1).append("fallbackPollAfter", new Date(2000));
            if (scenario.contains("missing-job")) fields.put("jobId", null);
            if ("poll-held".equals(scenario)) {
                Date held = new Date(System.currentTimeMillis() + 900_000L);
                fixedDates.put(held.getTime(), "<seed-query-lease>"); fields.put("fallbackPollAfter", held);
            }
            mongoTemplate.getCollection("transcode").updateOne(
                    new Document("_id", "fixture-transcode"), new Document("$set", fields));
            query(scenario.contains("success") || scenario.contains("registration") ? "TranscodeSuccess"
                    : scenario.contains("failed") ? "TranscodeFail" : "Transcoding", scenario.contains("query-error"));
            if (scenario.contains("registration")) doAnswer(invocation -> {
                hook("register.transcode", invocation.getArgument(0), invocation.getArgument(1));
                throw new TranscodeCallbackService.RegistrationException(new IllegalStateException("synthetic registration"));
            }).when(registrations).onSelfHostedTranscodeFinish(any(), any());
        } else {
            when(aliyunMpsService.submitFallbackTranscodeJob(anyString(), anyString(), anyString(), any()))
                    .thenAnswer(invocation -> {
                        calls.add(new JSONObject(Map.of("kind", "mps.fallback.submit", "inputKey", invocation.getArgument(0),
                                "playlistKey", invocation.getArgument(1), "resolution", invocation.getArgument(2),
                                "overrides", invocation.getArgument(3), "atSend", state())));
                        if ("submit-error".equals(scenario)) throw new IllegalStateException("synthetic MPS submit failure");
                        return "submit-unknown".equals(scenario) ? null : "fixture-fallback-job";
                    });
        }
        JSONObject row = begin("fallback-" + scenario);
        if (poll) service.pollFallbacks();
        else {
            JSONObject failure = new JSONObject(Map.of("taskId", task.getId(), "attemptId", ATTEMPT,
                    "status", "FAILED", "retryable", false, "errorMessage", "fixture exhausted"));
            row.put("returned", callbacks.handleCallback(failure));
            if ("submit-repeat".equals(scenario)) row.put("duplicateReturned", callbacks.handleCallback(failure));
            verify(aliyunMpsService, times(1)).submitFallbackTranscodeJob(anyString(), anyString(), anyString(), any());
        }
        if ("poll-success".equals(scenario)) assertEquals("SUCCEEDED", tasks.getById(task.getId()).getStatus());
        return end(row);
    }

    @Test
    void legacyProvidersAndMpsLeasesKeepTypedBsonPartialWritesAndDispatchOrder() throws Exception {
        MockMvc mvc = mvc();
        try {
            JSONArray cases = new JSONArray();
            for (String scenario : List.of("submit", "submit-error", "wire", "valid", "duplicate",
                    "secret-missing", "secret-wrong", "missing-job", "unknown-job", "null-job-on-record", "handler-error")) {
                cases.add(recordCf(mvc, scenario));
            }
            for (String scenario : List.of("submit", "submit-error", "valid", "TranscodeFail", "TranscodeCancelled",
                    "Transcoding", "Submitted", "same-status", "query-error", "unknown-job")) cases.add(recordMps(scenario));
            for (String scenario : List.of("submit", "submit-repeat", "submit-unknown", "submit-error",
                    "poll-active", "poll-held", "poll-success", "poll-failed", "poll-registration",
                    "poll-missing-job", "poll-missing-job-expired", "poll-query-error", "poll-query-error-expired")) {
                cases.add(recordFallback(scenario));
            }
            contract("legacy", cases);
        } finally {
            cleanDatabase();
        }
    }
}
