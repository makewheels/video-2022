package com.github.makewheels.video2022.transcode;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.openapi.webhook.WebhookEventPublisher;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.utils.IdService;
import com.github.makewheels.video2022.video.service.VideoReadyService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Execute the real LOCAL method through registration, without inventing a partial finish HTTP. */
class LocalOutputRegistrationContractTest extends BaseIntegrationTest {
    private static final String FOLDER = "fixture/local/";
    private static final String KEY = FOLDER + "playlist.m3u8";
    private static final String PLAYLIST = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n"
            + "#EXTINF:2.0,\na.ts\n#EXTINF:2.0,\nb.ts\n#EXT-X-ENDLIST\n";
    @Autowired private TranscodeCallbackService callbacks;
    @Autowired private TranscodeRepository transcodes;
    @Autowired @Qualifier("applicationEventMulticaster") private ApplicationEventMulticaster events;
    @MockitoBean private IdService ids;
    @MockitoBean private WebhookEventPublisher webhooks;
    @MockitoBean private VideoReadyService ready;

    private static class RegistrationComplete extends RuntimeException { }

    private JSONObject fixture(String scenario) {
        JSONObject input = new JSONObject(true);
        input.put("key", KEY); input.put("playlist", PLAYLIST); input.put("signedPlaylist", PLAYLIST);
        input.put("sidecar", "{\"schemaVersion\":1,\"transcodeId\":\"fixture-transcode\","
                + "\"playlistFilename\":\"playlist.m3u8\",\"width\":1280,\"height\":720,"
                + "\"frameRate\":\"30000/1001\",\"dynamicRange\":\"SDR\",\"hlsCodecs\":\"avc1.64001f,mp4a.40.2\"}");
        input.put("failedKey", null); input.put("failedCall", 1);
        input.put("signedFailure", false); input.put("listFailure", false); input.put("metadataNull", false);
        Map<String, Long> sizes = new LinkedHashMap<>();
        sizes.put(KEY, (long) PLAYLIST.length()); sizes.put(FOLDER + "a.ts", 100L); sizes.put(FOLDER + "b.ts", 150L);
        switch (scenario) {
            case "legacy-ts" -> input.put("sidecar", null);
            case "fmp4" -> {
                input.put("playlist", PLAYLIST.replace("a.ts", "a.m4s").replace("b.ts", "b.m4s")
                        .replace("#EXT-X-VERSION:3", "#EXT-X-VERSION:7\n#EXT-X-MAP:URI=\"init.mp4\""));
                sizes.remove(FOLDER + "a.ts"); sizes.remove(FOLDER + "b.ts");
                sizes.put(FOLDER + "a.m4s", 100L); sizes.put(FOLDER + "b.m4s", 150L);
                sizes.put(FOLDER + "init.mp4", 40L);
            }
            case "repeated-name" -> input.put("playlist", PLAYLIST.replace("#EXTINF:2.0,\nb.ts", "#EXTINF:3.0,\na.ts"));
            case "missing-segment" -> sizes.remove(FOLDER + "a.ts");
            case "sidecar-read-failure" -> input.put("failedKey", FOLDER + "result.json");
            case "sidecar-null" -> input.put("sidecar", "null");
            case "sidecar-empty" -> input.put("sidecar", "");
            case "sidecar-malformed" -> input.put("sidecar", "{");
            case "sidecar-lenient" -> input.put("sidecar", "{,,/* old */schemaVersion:1,transcodeId:'fixture-transcode',"
                    + "playlistFilename:'playlist.m3u8',width:1280,height:720,frameRate:'30/1',,}");
            case "sidecar-schema-string" -> metadata(input, "schemaVersion", "1");
            case "sidecar-schema-decimal" -> metadata(input, "schemaVersion", 1.99);
            case "sidecar-schema-wrap" -> metadata(input, "schemaVersion", 4294967297L);
            case "sidecar-schema-null" -> metadata(input, "schemaVersion", null);
            case "sidecar-schema-missing" -> {
                JSONObject sidecar = JSON.parseObject(input.getString("sidecar"));
                sidecar.remove("schemaVersion"); input.put("sidecar", sidecar.toJSONString());
            }
            case "sidecar-schema-spaces" -> metadata(input, "schemaVersion", " 1 ");
            case "sidecar-width-string" -> metadata(input, "width", "1280");
            case "sidecar-width-decimal" -> metadata(input, "width", 1280.75);
            case "sidecar-width-null" -> metadata(input, "width", null);
            case "sidecar-width-array" -> metadata(input, "width", List.of(1280));
            case "sidecar-wrong-width" -> metadata(input, "width", 1278);
            case "sidecar-wrong-id" -> metadata(input, "transcodeId", "other-transcode");
            case "sidecar-wrong-playlist" -> metadata(input, "playlistFilename", "other.m3u8");
            case "sidecar-clear" -> {
                JSONObject sidecar = JSON.parseObject(input.getString("sidecar"));
                for (String name : List.of("frameRate", "dynamicRange", "hlsCodecs")) sidecar.remove(name);
                input.put("sidecar", sidecar.toJSONString());
            }
            case "sidecar-string-coercion" -> {
                metadata(input, "frameRate", List.of(30, 1));
                input.put("sidecar", input.getString("sidecar").replace("\"dynamicRange\":\"SDR\"",
                        "\"dynamicRange\":" + getterObject()));
            }
            case "sidecar-invalid-codec" -> metadata(input, "hlsCodecs", "avc1.64001f\n");
            case "sidecar-empty-codec" -> metadata(input, "hlsCodecs", "");
            case "crlf" -> input.put("playlist", PLAYLIST.replace("\n", "\r\n"));
            case "extinf-spaces" -> input.put("playlist", PLAYLIST.replace("#EXTINF:", " #EXTINF:"));
            case "filename-spaces" -> input.put("playlist", PLAYLIST.replace("a.ts\n", "a.ts \n")
                    .replace("b.ts\n", "\tb.ts\n"));
            case "invalid-extinf" -> input.put("playlist", PLAYLIST.replace("#EXTINF:2.0,", "#EXTINF:NaN,"));
            case "missing-extinf-comma" -> input.put("playlist", PLAYLIST.replace("#EXTINF:2.0,", "#EXTINF:2.0"));
            case "trailing-extinf" -> input.put("playlist", PLAYLIST + "#EXTINF:2.0,");
            case "short-playlist" -> input.put("playlist", "#EXTM3U\na.ts\n#EXT-X-ENDLIST");
            case "half-up", "negative-half-up" -> {
                input.put("playlist", PLAYLIST.replace("#EXTINF:2.0,", "#EXTINF:16,"));
                sizes.put(FOLDER + "a.ts", 1L); sizes.put(FOLDER + "b.ts", 1L);
            }
            case "long-overflow" -> {
                sizes.put(FOLDER + "a.ts", Long.MAX_VALUE); sizes.put(FOLDER + "b.ts", Long.MAX_VALUE);
            }
            case "int-overflow" -> sizes.put(FOLDER + "a.ts", (long) Integer.MAX_VALUE);
            case "average-overflow" -> {
                input.put("playlist", PLAYLIST.replace("#EXTINF:2.0,", "#EXTINF:1000000,"));
                sizes.put(FOLDER + "a.ts", 2000000000L); sizes.put(FOLDER + "b.ts", 2000000000L);
            }
            case "signed-changed" -> input.put("signedPlaylist", PLAYLIST.replace("#EXTINF:2.0,\nb.ts\n", ""));
            case "signed-empty" -> input.put("signedPlaylist", "");
            case "signed-failure" -> input.put("signedFailure", true);
            case "listing-failure" -> input.put("listFailure", true);
            case "metadata-null" -> input.put("metadataNull", true);
            case "metadata-failure" -> { input.put("failedKey", KEY); input.put("failedCall", 2); }
            default -> { }
        }
        if (!List.of("signed-changed", "signed-empty").contains(scenario)) {
            input.put("signedPlaylist", input.get("playlist"));
        }
        JSONArray summaries = new JSONArray();
        sizes.forEach((key, size) -> summaries.add(new JSONObject(new LinkedHashMap<>(Map.of(
                "key", key, "size", scenario.equals("negative-half-up") && key.endsWith(".ts") ? -1L : size,
                "etag", "fixture-etag", "storageClass", "Standard", "uploadTime", 2000)))));
        if (scenario.equals("missing-summary")) summaries.removeIf(item -> ((JSONObject) item).getString("key").endsWith("a.ts"));
        if (scenario.equals("late-missing-summary")) summaries.removeIf(item -> ((JSONObject) item).getString("key").endsWith("b.ts"));
        if (scenario.equals("duplicate-basename")) {
            for (String folder : List.of("extra-one/", "extra-two/")) {
                summaries.add(new JSONObject(Map.of("key", FOLDER + folder + "ignored.txt", "size", 1L,
                        "etag", "ignored", "storageClass", "Standard", "uploadTime", 2000)));
            }
        }
        input.put("sizes", sizes); input.put("summaries", summaries);
        return input;
    }

    private void metadata(JSONObject input, String field, Object value) {
        JSONObject sidecar = JSON.parseObject(input.getString("sidecar"));
        sidecar.put(field, value);
        input.put("sidecar", JSON.toJSONString(sidecar, SerializerFeature.WriteMapNullValue));
    }

    private String getterObject() {
        StringBuilder object = new StringBuilder(
                "{\"z\":7,\"a\":1,\"p\":2,\"emoji\":\"\\ud83d\\ude00\",\"tree\":{");
        for (int index = 0; index < 32; index++) {
            if (index > 0) object.append(',');
            StringBuilder key = new StringBuilder();
            for (int bit = 0; bit < 5; bit++) key.append((index & (1 << bit)) == 0 ? "Aa" : "BB");
            object.append('"').append(key).append("\":").append(index);
        }
        return object.append("}}").toString();
    }

    private void validator(String collection, Document rule) {
        if (!mongoTemplate.collectionExists(collection)) mongoTemplate.createCollection(collection);
        mongoTemplate.getDb().runCommand(new Document("collMod", collection).append("validator", rule));
    }

    private void seed(String scenario) {
        cleanDatabase();
        Document video = new Document("_id", "fixture-video").append("uploaderId", "fixture-uploader")
                .append("videoType", "USER_UPLOAD").append("status", "TRANSCODING")
                .append("mediaInfo", new Document("duration", 4000L)).append("untouched", "keep");
        if (scenario.equals("missing-media")) video.put("mediaInfo", null);
        if (scenario.equals("missing-duration")) video.put("mediaInfo", new Document("duration", null));
        if (scenario.equals("zero-duration")) video.put("mediaInfo", new Document("duration", 0L));
        if (scenario.equals("negative-duration")) video.put("mediaInfo", new Document("duration", -4000L));
        if (scenario.equals("string-duration")) video.put("mediaInfo", new Document("duration", "4000"));
        if (scenario.equals("decimal-duration")) video.put("mediaInfo", new Document("duration", 4000.75));
        if (List.of("half-up", "negative-half-up").contains(scenario)) {
            video.put("mediaInfo", new Document("duration", 32000L));
        }
        if (scenario.equals("average-overflow")) video.put("mediaInfo", new Document("duration", 1L));
        if (!scenario.equals("missing-video")) mongoTemplate.getCollection("video").insertOne(video);
        Document transcode = new Document("_id", "fixture-transcode").append("videoId", "fixture-video")
                .append("provider", "LOCAL").append("resolution", "720p").append("status", "TRANSCODING")
                .append("actualWidth", 1280).append("actualHeight", 720).append("createTime", new Date(1000))
                .append("actualFrameRate", "24/1").append("actualDynamicRange", "old").append("actualCodecs", "old")
                .append("averageBitrate", 77).append("maxBitrate", 99).append("m3u8Key", KEY)
                .append("untouched", "drop-only-on-save");
        if (scenario.equals("null-task-width")) transcode.put("actualWidth", null);
        if (scenario.equals("decimal-task-width")) transcode.put("actualWidth", 1280.75);
        if (scenario.equals("missing-create-time")) transcode.remove("createTime");
        mongoTemplate.getCollection("transcode").insertOne(transcode);
        if (List.of("existing-playlist", "repeated-prefix", "existing-object-id").contains(scenario)) {
            Object id = scenario.equals("existing-object-id") ? new ObjectId("0123456789abcdef01234567") : "existing-playlist";
            mongoTemplate.getCollection("file").insertOne(new Document("_id", id).append("key", KEY)
                    .append("fileStatus", "OLD").append("untouched", "keep"));
        }
        if (List.of("existing-first", "repeated-prefix", "no-bitrates").contains(scenario)) {
            mongoTemplate.getCollection("tsFile").insertOne(segment("existing-first", 0, 100L, 123));
        }
        if (List.of("repeated-prefix", "no-bitrates").contains(scenario)) {
            mongoTemplate.getCollection("tsFile").insertOne(segment("existing-second", 1, 150L, 456));
        }
        if (scenario.equals("no-bitrates")) mongoTemplate.getCollection("tsFile")
                .updateMany(new Document(), new Document("$set", new Document("bitrate", null)));
        if (scenario.equals("history")) mongoTemplate.getCollection("tsFile")
                .insertOne(segment("z-historical", 9, 250L, 1000));
        if (scenario.equals("null-index-history")) mongoTemplate.getCollection("tsFile")
                .insertOne(segment("z-historical", null, 250L, 1000));
        if (scenario.equals("null-size-history")) mongoTemplate.getCollection("tsFile")
                .insertOne(segment("z-historical", 9, null, 1000));
        if (scenario.equals("file-write-failure")) validator("file", new Document("fileStatus", "REJECT"));
        if (scenario.equals("batch-write-failure")) validator("tsFile", new Document("tsIndex", new Document("$ne", 1)));
        if (scenario.equals("transcode-write-failure")) {
            validator("transcode", new Document("m3u8Content", new Document("$exists", false)));
        }
    }

    private Document segment(String id, Integer index, Long size, Integer bitrate) {
        return new Document("_id", id).append("transcodeId", "fixture-transcode").append("tsIndex", index)
                .append("size", size).append("bitrate", bitrate).append("untouched", "keep");
    }

    private Object normalize(Object value) {
        if (value instanceof Date date) return date.getTime() < 10000 ? date.getTime() : "<server-date>";
        if (value instanceof ObjectId id) return Map.of("$oid", id.toHexString());
        if (value instanceof List<?> list) return list.stream().map(this::normalize).toList();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((name, item) -> result.put(name.toString(), normalize(item)));
            return result;
        }
        return value;
    }

    private JSONObject state() {
        JSONObject state = new JSONObject(true);
        for (String collection : List.of("video", "file", "tsFile", "transcode")) {
            state.put(collection, normalize(mongoTemplate.getCollection(collection).find()
                    .sort(new Document("_id", 1)).into(new ArrayList<>())));
        }
        return state;
    }

    private JSONObject record(String scenario) {
        seed(scenario);
        reset(ossVideoService, ids, webhooks, ready);
        JSONObject input = fixture(scenario), row = new JSONObject(true);
        List<String> calls = new ArrayList<>(), prefixes = new ArrayList<>();
        AtomicInteger sequence = new AtomicInteger();
        when(ids.getFileId()).thenAnswer(invocation -> { prefixes.add("f_"); return "f_fixture_created"; });
        when(ids.getTsFileId()).thenAnswer(invocation -> {
            prefixes.add("f_ts_"); return "f_ts_fixture_created_" + sequence.getAndIncrement();
        });
        when(ossVideoService.getObject(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            calls.add("get:" + key);
            long count = calls.stream().filter(item -> item.equals("get:" + key)).count();
            if (key.equals(input.getString("failedKey")) && count == input.getIntValue("failedCall")) {
                throw new OSSException("synthetic object failure");
            }
            if (key.equals(KEY) && count == 2 && input.getBooleanValue("metadataNull")) return null;
            String content = key.endsWith("result.json") ? input.getString("sidecar")
                    : key.equals(KEY) ? input.getString("playlist") : "x";
            if (content == null || (!key.endsWith("result.json") && !input.getJSONObject("sizes").containsKey(key))) return null;
            OSSObject object = new OSSObject();
            object.setKey(key); object.setObjectContent(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(key.endsWith("result.json") ? content.length() : input.getJSONObject("sizes").getLongValue(key));
            metadata.setHeader("ETag", "fixture-etag"); metadata.setLastModified(new Date(2000));
            object.setObjectMetadata(metadata);
            return object;
        });
        when(ossVideoService.generatePresignedUrl(anyString(), any(Duration.class))).thenAnswer(invocation -> {
            calls.add("sign:" + invocation.getArgument(0) + ":" + ((Duration) invocation.getArgument(1)).toSeconds());
            return "https://example.invalid/fixture-playlist";
        });
        when(ossVideoService.listAllObjects(anyString())).thenAnswer(invocation -> {
            calls.add("list:" + invocation.getArgument(0));
            if (input.getBooleanValue("listFailure")) throw new IllegalStateException("synthetic listing failure");
            List<OSSObjectSummary> result = new ArrayList<>();
            for (Object value : input.getJSONArray("summaries")) {
                JSONObject item = (JSONObject) value;
                OSSObjectSummary summary = new OSSObjectSummary();
                summary.setKey(item.getString("key")); summary.setSize(item.getLongValue("size"));
                summary.setETag(item.getString("etag")); summary.setStorageClass(item.getString("storageClass"));
                summary.setLastModified(new Date(item.getLongValue("uploadTime"))); result.add(summary);
            }
            return result;
        });
        ApplicationListener<ApplicationEvent> boundary = event -> {
            // Mongo has committed this save; stop before the original method's finishTranscode call.
            if (event instanceof AfterSaveEvent<?> saved && saved.getSource() instanceof Transcode value
                    && "fixture-transcode".equals(value.getId())) {
                throw new RegistrationComplete();
            }
        };
        row.put("scenario", scenario); row.put("input", input); row.put("before", state());
        events.addApplicationListener(boundary);
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get("https://example.invalid/fixture-playlist")).thenAnswer(invocation -> {
                calls.add("download:https://example.invalid/fixture-playlist");
                if (input.getBooleanValue("signedFailure")) throw new IllegalStateException("synthetic signed failure");
                return input.getString("signedPlaylist");
            });
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> callbacks.onLocalTranscodeFinish(transcodes.getById("fixture-transcode")));
            row.put("registered", failure instanceof RegistrationComplete);
            String message = failure instanceof IllegalStateException ? failure.getMessage() : null;
            // JDK's duplicate-map diagnostic suffix contains random OSSObjectSummary identities.
            if (message != null && message.startsWith("Duplicate key ")) {
                message = message.substring(0, message.indexOf(" (attempted merging values "));
            }
            row.put("error", message);
        } finally {
            events.removeApplicationListener(boundary);
        }
        row.put("after", state()); row.put("calls", calls); row.put("idPrefixes", prefixes);
        assertEquals("TRANSCODING", mongoTemplate.getCollection("transcode").find().first().getString("status"));
        verifyNoInteractions(webhooks, ready, aliyunMpsService, cloudFunctionTranscodeService, md5CfService, youtubeService);
        for (Document document : mongoTemplate.getCollection("tsFile").find(new Document("fileStatus", "READY"))) {
            assertInstanceOf(Long.class, document.get("size"));
            assertFalse(document.containsKey("timeLength"));
            assertFalse(document.containsKey("createTime"));
        }
        return row;
    }

    @Test
    void realLocalRegistrationPrefixAndPartialWritesMatchFixedCompleteBson() throws Exception {
        JSONArray cases = new JSONArray();
        try {
            for (String scenario : List.of("legacy-ts", "sidecar-ts", "fmp4", "repeated-prefix", "existing-playlist",
                    "existing-object-id", "existing-first", "history", "null-index-history", "null-size-history",
                    "no-bitrates", "repeated-name", "missing-summary", "late-missing-summary", "duplicate-basename",
                    "missing-segment", "sidecar-read-failure", "sidecar-null", "sidecar-empty", "sidecar-malformed",
                    "sidecar-lenient", "sidecar-schema-string", "sidecar-schema-decimal", "sidecar-schema-wrap",
                    "sidecar-schema-null", "sidecar-schema-missing", "sidecar-schema-spaces", "sidecar-width-string",
                    "sidecar-width-decimal", "sidecar-width-null", "sidecar-width-array", "sidecar-wrong-width",
                    "sidecar-wrong-id", "sidecar-wrong-playlist", "sidecar-clear", "sidecar-string-coercion",
                    "sidecar-invalid-codec", "sidecar-empty-codec", "null-task-width", "missing-media", "missing-duration",
                    "zero-duration", "negative-duration", "string-duration", "decimal-duration", "decimal-task-width",
                    "half-up", "negative-half-up", "long-overflow", "int-overflow",
                    "average-overflow", "crlf", "extinf-spaces", "filename-spaces", "invalid-extinf",
                    "missing-extinf-comma", "trailing-extinf", "short-playlist", "signed-changed", "signed-empty",
                    "signed-failure", "listing-failure", "metadata-null", "metadata-failure", "file-write-failure",
                    "batch-write-failure", "transcode-write-failure", "missing-create-time", "missing-video")) {
                cases.add(record(scenario));
            }
            assertTrue(cases.getJSONObject(0).getBooleanValue("registered"), "Real AfterSave boundary must be reached");
            String serialized = JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat);
            String output = System.getProperty("contract.local-output.output.dir");
            if (output != null) {
                Files.createDirectories(Path.of(output)); Files.writeString(Path.of(output, "cases.json"), serialized);
            } else {
                assertEquals(JSON.parseArray(Files.readString(Path.of("../../serverless/contracts/local-output-registration/cases.json"))),
                        JSON.parseArray(serialized));
            }
        } finally {
            cleanDatabase();
        }
    }
}
