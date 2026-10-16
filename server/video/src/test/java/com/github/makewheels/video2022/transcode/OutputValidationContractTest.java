package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Executes the real old validator with synthetic object reads; no Spring context, DB or cloud. */
class OutputValidationContractTest {
    @Test
    void playlistManifestObjectChecksAndReadOrderMatchCommittedCorpus() throws Exception {
        String key = "fixture/root/playlist.m3u8";
        String standard = "#EXTM3U\n#EXTINF:6.0,\nseg0.ts\n#EXT-X-ENDLIST\n";
        JSONArray cases = new JSONArray();
        for (String scenario : List.of("success", "key-null", "key-mismatch", "playlist-missing", "playlist-empty",
                "playlist-blank", "missing-endlist", "no-segments", "unsafe-segment", "unicode-segment",
                "missing-segment", "empty-segment", "fmp4", "missing-init", "unsafe-init", "map-attributes",
                "manifest-complete", "manifest-missing", "manifest-duplicate", "manifest-outside", "manifest-zero",
                "manifest-size", "manifest-read-failure", "segment-read-failure", "crlf", "duplicate-segment")) {
            String playlist = switch (scenario) {
                case "playlist-empty" -> "";
                case "playlist-blank" -> " \t\r\n";
                case "missing-endlist" -> standard.replace("#EXT-X-ENDLIST", "");
                case "no-segments" -> "#EXTM3U\n#EXT-X-ENDLIST\n";
                case "unsafe-segment" -> standard.replace("seg0.ts", "../seg0.ts");
                case "unicode-segment" -> standard.replace("seg0.ts", "片段.ts");
                case "fmp4", "missing-init" -> standard.replace("#EXTM3U", "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"");
                case "unsafe-init" -> standard.replace("#EXTM3U", "#EXTM3U\n#EXT-X-MAP:URI=\"../init.mp4\"");
                case "map-attributes" -> standard.replace("#EXTM3U", "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\",BYTERANGE=\"24@0\"");
                case "crlf" -> standard.replace("\n", "\r\n");
                case "duplicate-segment" -> standard.replace("#EXT-X-ENDLIST", "#EXTINF:6.0,\nseg0.ts\n#EXT-X-ENDLIST");
                default -> standard;
            };
            Map<String, Long> sizes = new HashMap<>();
            sizes.put(key, (long) playlist.getBytes(StandardCharsets.UTF_8).length);
            sizes.put("fixture/root/seg0.ts", 42L);
            sizes.put("fixture/root/init.mp4", 24L);
            if (scenario.equals("playlist-missing")) sizes.remove(key);
            if (scenario.equals("missing-segment")) sizes.remove("fixture/root/seg0.ts");
            if (scenario.equals("missing-init")) sizes.remove("fixture/root/init.mp4");
            if (scenario.equals("empty-segment")) sizes.put("fixture/root/seg0.ts", 0L);
            JSONObject manifest = new JSONObject(true);
            manifest.put("playlistKey", scenario.equals("key-null") ? null : scenario.equals("key-mismatch") ? "other" : key);
            if (scenario.startsWith("manifest-")) {
                JSONArray objects = new JSONArray();
                objects.add(new JSONObject(Map.of("key", key, "size", sizes.get(key))));
                if (!scenario.equals("manifest-missing")) {
                    objects.add(new JSONObject(Map.of("key", "fixture/root/seg0.ts", "size",
                            scenario.equals("manifest-zero") ? 0 : scenario.equals("manifest-size") ? 41 : 42)));
                }
                if (scenario.equals("manifest-duplicate")) objects.add(objects.get(0));
                if (scenario.equals("manifest-outside")) objects.add(new JSONObject(Map.of("key", "other", "size", 1)));
                manifest.put("objects", objects);
            }
            String failedKey = scenario.equals("manifest-read-failure") || scenario.equals("segment-read-failure")
                    ? "fixture/root/seg0.ts" : null;
            int failedCall = scenario.equals("manifest-read-failure") ? 2 : 1;
            List<String> calls = new ArrayList<>();
            Map<String, Integer> counts = new HashMap<>();
            FileService files = mock(FileService.class);
            when(files.getObject(anyString())).thenAnswer(invocation -> {
                String requested = invocation.getArgument(0);
                calls.add(requested);
                int count = counts.merge(requested, 1, Integer::sum);
                if (requested.equals(failedKey) && count == failedCall) throw new IllegalStateException("synthetic read failure");
                Long size = sizes.get(requested);
                if (size == null) return null;
                OSSObject object = new OSSObject();
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(size);
                object.setObjectMetadata(metadata);
                object.setObjectContent(new ByteArrayInputStream((requested.equals(key) ? playlist : "x")
                        .getBytes(StandardCharsets.UTF_8)));
                return object;
            });
            OutputValidator validator = new OutputValidator();
            ReflectionTestUtils.setField(validator, "fileService", files);
            Transcode transcode = new Transcode();
            transcode.setM3u8Key(key);
            JSONObject row = new JSONObject(true);
            row.put("scenario", scenario);
            row.put("key", key);
            row.put("playlist", playlist);
            row.put("manifest", manifest);
            row.put("sizes", sizes);
            row.put("failedKey", failedKey);
            row.put("failedCall", failedCall);
            row.put("error", validator.validate(transcode, manifest));
            row.put("calls", calls);
            cases.add(row);
        }
        String serialized = JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat);
        String output = System.getProperty("contract.output-validation.dir");
        if (output != null) {
            Files.createDirectories(Path.of(output));
            Files.writeString(Path.of(output, "cases.json"), serialized);
        } else {
            assertEquals(JSON.parseArray(Files.readString(Path.of("../../serverless/contracts/output-validation/cases.json"))),
                    JSON.parseArray(serialized));
        }
    }
}
