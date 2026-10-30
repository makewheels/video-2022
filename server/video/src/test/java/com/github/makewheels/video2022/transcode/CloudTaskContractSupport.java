package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionClient;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.mps.MpsFallbackService;
import com.github.makewheels.video2022.transcode.task.FcTask;
import com.github.makewheels.video2022.transcode.task.FcTaskCallbackService;
import com.github.makewheels.video2022.transcode.task.FcTaskRepository;
import com.github.makewheels.video2022.transcode.task.FcTaskSubmitter;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = "transcode.recovery.enabled=false")
abstract class CloudTaskContractSupport extends BaseIntegrationTest {
    protected static final String CPU = TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU;
    protected static final String GPU = TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU;
    protected static final String ATTEMPT = "fixture-attempt";
    protected static final String BASE = "fixture/output";
    protected static final String DIR = BASE + "/attempts/" + ATTEMPT;
    protected static final String SECRET = "fixture-cloud-callback";
    @Autowired protected WebApplicationContext context;
    @Autowired protected FcTaskRepository tasks;
    @Autowired protected FcTaskSubmitter submitter;
    @Autowired protected FcTaskCallbackService callbacks;
    @Autowired protected CloudFunctionClient client;
    @Autowired protected MpsFallbackService fallback;
    @Autowired protected TranscodeController controller;
    @MockitoBean protected FileService files;
    @MockitoBean protected TranscodeCallbackService registrations;
    @MockitoBean protected TranscodeLauncher launcher;
    protected final List<JSONObject> calls = new CopyOnWriteArrayList<>();
    protected final Map<String, String> generated = new HashMap<>();
    protected final Map<Long, String> fixedDates = new HashMap<>();
    protected long started;
    protected long finished;
    protected boolean jitter;

    protected void startCase(String provider) {
        assertTrue(mongoTemplate.getDb().getName().contains("test"), "Never clean a non-test database");
        cleanDatabase();
        reset(files, registrations, launcher, aliyunMpsService, cloudFunctionTranscodeService);
        calls.clear(); generated.clear(); fixedDates.clear(); jitter = false;
        ReflectionTestUtils.setField(client, "bucket", "fixture-bucket");
        ReflectionTestUtils.setField(client, "internalEndpoint", "https://oss.example.invalid");
        ReflectionTestUtils.setField(client, "invokeSecret", "fixture-invocation-only");
        ReflectionTestUtils.setField(controller, "fcCallbackSecret", SECRET);
        ReflectionTestUtils.setField(controller, "callbackSecret", SECRET);
        ReflectionTestUtils.setField(callbacks, "retryBackoffMs", 10_000L);
        ReflectionTestUtils.setField(callbacks, "retryJitterMs", 0L);
        ReflectionTestUtils.setField(callbacks, "mpsFallbackService", fallback);
        ReflectionTestUtils.setField(fallback, "fallbackTemplate720p", "");
        ReflectionTestUtils.setField(fallback, "fallbackTemplate1080p", "");
        Document media = new Document("duration", 6000L).append("width", 1280).append("height", 720)
                .append("dynamicRange", "SDR").append("frameRate", "30/1").append("frameRateMode", "CFR")
                .append("audioTrackCount", 0).append("audioTracks", new Document("tracks", List.of()))
                .append("bitDepth", 8).append("rotation", 0).append("sar", "1:1");
        mongoTemplate.getCollection("video").insertOne(new Document("_id", "fixture-video")
                .append("uploaderId", "fixture-uploader").append("videoType", "USER_UPLOAD")
                .append("status", "TRANSCODING").append("transcodeIds", List.of("fixture-transcode"))
                .append("createTime", new Date(1000)).append("mediaInfo", media).append("untouched", "keep"));
        mongoTemplate.getCollection("transcode").insertOne(new Document("_id", "fixture-transcode")
                .append("videoId", "fixture-video").append("provider", provider).append("resolution", "720p")
                .append("sourceKey", "fixture/raw/source.mp4").append("m3u8Key", BASE + "/playlist.m3u8")
                .append("status", "TRANSCODING").append("createTime", new Date(1000))
                .append("jobId", "fixture-legacy-job").append("taskId", "fc-TRANSCODE-fixture-transcode")
                .append("untouched", "drop-only-on-save").append("nullable", null));
        mongoTemplate.getCollection("cover").insertOne(new Document("_id", "fixture-cover")
                .append("videoId", "fixture-video").append("fileId", "fixture-image").append("untouched", "keep"));
        mongoTemplate.getCollection("file").insertOne(new Document("_id", "fixture-image")
                .append("key", BASE + "/cover.jpg").append("size", 1L << 40)
                .append("metaObjectId", new ObjectId("0123456789abcdef01234567")));
        doAnswer(invocation -> {
            hook("register.transcode", invocation.getArgument(0), invocation.getArgument(1)); return null;
        }).when(registrations).onSelfHostedTranscodeFinish(any(), any());
        doAnswer(invocation -> {
            hook("register.cover", invocation.getArgument(0), invocation.getArgument(1)); return null;
        }).when(registrations).onFcCoverFinished(any(), any());
        doAnswer(invocation -> {
            JSONObject call = new JSONObject(true);
            call.put("kind", "register.probe"); call.put("manifest", invocation.getArgument(1));
            call.put("atDispatch", state()); calls.add(call); return null;
        }).when(launcher).onProbeFinished(any(), any());
        doAnswer(invocation -> {
            hook("task.failed", invocation.getArgument(0), invocation.getArgument(1)); return null;
        }).when(registrations).onFcTaskFailed(any(), nullable(String.class));
        doAnswer(invocation -> {
            JSONObject call = new JSONObject(true);
            call.put("kind", "register.legacy"); call.put("atDispatch", state()); calls.add(call); return null;
        }).when(registrations).onTranscodeFinish(any());
    }

    protected FcTask task(String operation, String provider) {
        String ref = "PROBE".equals(operation) ? null
                : "COVER".equals(operation) ? "fixture-cover" : "fixture-transcode";
        FcTask task = submitter.createTask(operation, "fixture-video", ref);
        task.setProvider(provider); task.setAttemptId(ATTEMPT); task.setAttemptCount(1);
        task.setCreateTime(new Date(1000)); task.setDeadline(new Date(2000));
        task.setExecutionTimeoutMs(300_000L); task.setOutputBaseDir(BASE);
        JSONObject payload = client.buildPayload(task, "TRANSCODE".equals(operation) ? ref : null,
                "fixture/raw/source.mp4", BASE, "https://example.invalid/transcode/cloudFunctionCallback");
        if ("TRANSCODE".equals(operation)) {
            payload.put("playlistKey", BASE + "/playlist.m3u8");
            payload.put("profile", "fixture-720p");
            payload.put("mediaPolicy", new JSONObject(Map.of(
                    "outputWidth", 1280, "outputHeight", 720, "targetBitrateBps", 4_000_000L)));
        }
        if ("COVER".equals(operation)) { payload.put("imageKey", BASE + "/cover.jpg"); payload.put("atSeconds", 0); }
        task.setPayloadSnapshot(payload);
        return task;
    }

    protected void store(FcTask task, String status) {
        task.setStatus(status);
        if (!"CREATED".equals(status)) {
            task.getPayloadSnapshot().put("outputDir", DIR);
            if ("TRANSCODE".equals(task.getOperation())) task.getPayloadSnapshot().put("playlistKey", DIR + "/playlist.m3u8");
            if ("COVER".equals(task.getOperation())) task.getPayloadSnapshot().put("imageKey", DIR + "/cover.jpg");
        }
        Document document = new Document();
        mongoTemplate.getConverter().write(task, document);
        document.put("untouched", "keep"); document.put("nullable", null);
        mongoTemplate.getCollection("fcTask").insertOne(document);
    }

    protected JSONObject state() {
        JSONObject result = new JSONObject(true);
        for (String collection : List.of("fcTask", "video", "transcode", "cover", "file", "tsFile")) {
            result.put(collection, mongoTemplate.getCollection(collection).find()
                    .sort(new Document("_id", 1)).into(new ArrayList<>()));
        }
        return result;
    }

    protected void hook(String kind, FcTask task, Object value) {
        JSONObject call = new JSONObject(true);
        call.put("kind", kind); call.put("taskId", task.getId()); call.put("attemptId", task.getAttemptId());
        call.put("value", value); call.put("atDispatch", state()); calls.add(call);
    }

    protected JSONObject begin(String scenario) {
        JSONObject row = new JSONObject(true);
        row.put("scenario", scenario); row.put("before", state());
        started = System.currentTimeMillis();
        return row;
    }

    protected JSONObject end(JSONObject row) {
        finished = System.currentTimeMillis();
        row.put("after", state()); row.put("calls", new ArrayList<>(calls));
        row.put("requestLogs", mongoTemplate.getCollection("requestLog").countDocuments());
        row.put("exceptionLogs", mongoTemplate.getCollection("exceptionLog").countDocuments());
        for (Document document : mongoTemplate.getCollection("fcTask").find()) {
            String attempt = document.getString("attemptId");
            if (attempt != null && !ATTEMPT.equals(attempt) && attempt.startsWith(ATTEMPT + "-")) {
                assertEquals(6, attempt.length() - ATTEMPT.length() - 1);
                generated.put(attempt, "<next-attempt>");
            }
        }
        return (JSONObject) JSON.toJSON(normalize(row, "", false));
    }

    private Object date(Date date, String field) {
        long value = date.getTime();
        if (value < 10_000) return value;
        if (fixedDates.containsKey(value)) return fixedDates.get(value);
        if (jitter && "deadline".equals(field)) {
            assertTrue(value >= started + 10_000 && value <= finished + 12_999);
            return "<action+10000..12999ms>";
        }
        List<Long> offsets = "finishTime".equals(field) ? List.of(0L)
                : List.of(1_800_000L, 300_000L, 60_000L, 10_000L, 0L);
        for (long offset : offsets) {
            if (value >= started + offset && value <= finished + offset) return "<action+" + offset + "ms>";
        }
        fail("Unexpected " + field + " date offset: " + (value - started));
        return null;
    }

    private Object normalize(Object value, String field, boolean bson) {
        if (value instanceof Date date) return Map.of("$date", date(date, field));
        if (value instanceof ObjectId id) return Map.of("$oid", id.toHexString());
        if (value instanceof String text) return generated.getOrDefault(text, text);
        if (value instanceof List<?> list) return list.stream().map(item -> normalize(item, field, bson)).toList();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String name = key.toString();
                boolean typed = bson || List.of("before", "after", "atSend", "atDispatch", "atQuery").contains(name);
                result.put(name, normalize(item, name, typed));
            });
            return result;
        }
        if (bson && value instanceof Integer number) return Map.of("$numberInt", number.toString());
        if (bson && value instanceof Long number) return Map.of("$numberLong", number.toString());
        if (bson && value instanceof Double number) return Map.of("$numberDouble", number.toString());
        return value;
    }

    protected MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(context).build(); }

    protected void contract(String name, JSONArray cases) throws Exception {
        HashSet<String> identities = new HashSet<>();
        for (Object value : cases) assertTrue(identities.add(((JSONObject) value).getString("scenario")));
        String text = JSON.toJSONString(cases, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue);
        String output = System.getProperty("contract.cloud-task.output.dir");
        if (output != null) {
            Files.createDirectories(Path.of(output)); Files.writeString(Path.of(output, name + ".json"), text);
        } else {
            assertEquals(JSON.parseArray(Files.readString(Path.of(
                    "..", "..", "serverless", "contracts", "cloud-task-protocol", name + ".json"))), JSON.parseArray(text));
        }
    }
}
