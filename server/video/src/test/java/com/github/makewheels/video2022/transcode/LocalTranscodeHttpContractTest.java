package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.BaseIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real old HTTP and all registered lanes; no cloud transcode, probe or snapshot. */
class LocalTranscodeHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;

    private JSONObject state() {
        JSONObject state = new JSONObject(true);
        for (String collection : List.of("video", "file", "transcode")) {
            JSONArray rows = new JSONArray();
            for (Document row : mongoTemplate.getCollection(collection).find().sort(new Document("_id", 1))) {
                for (String field : List.of("createTime", "updateTime", "finishTime")) {
                    if (row.get(field) != null) {
                        assertInstanceOf(Date.class, row.get(field));
                        row.put(field, "<server-date>");
                    }
                }
                rows.add(row);
            }
            state.put(collection, rows);
        }
        return state;
    }

    private String seed(String scenario) {
        cleanDatabase();
        reset(ossVideoService);
        JSONObject credentials = JSON.parseObject("{\"bucket\":\"fixture-bucket\",\"key\":\"fixture-key\","
                + "\"endpoint\":\"https://example.invalid\",\"accessKeyId\":\"fixture-not-real\","
                + "\"secretKey\":\"fixture-not-real\",\"sessionToken\":\"fixture-not-real\","
                + "\"expiration\":\"2030-01-01T00:00:00Z\"}");
        when(ossVideoService.generateUploadCredentials(anyString())).thenReturn(credentials);
        if (scenario.equals("credentials-null")) doReturn(null).when(ossVideoService).generateUploadCredentials(anyString());
        if (scenario.equals("credentials-failure")) {
            doThrow(new IllegalStateException("synthetic STS failure"))
                    .when(ossVideoService).generateUploadCredentials(anyString());
        }
        mongoTemplate.getCollection("user").insertOne(new Document("_id", "fixture-uploader")
                .append("token", "fixture-local-token"));
        Document video = new Document("_id", "fixture-video").append("uploaderId", "fixture-uploader")
                .append("ownerId", "other-owner").append("videoType", "USER_UPLOAD")
                .append("transcodeMode", scenario.equals("auto-mode") ? "AUTO" : "LOCAL")
                .append("rawFileId", "fixture-raw").append("status", scenario.equals("ready-video") ? "READY" : "CREATED")
                .append("createTime", new Date(1705276800000L));
        if (scenario.equals("existing-media")) video.put("mediaInfo", new Document("duration", 0L).append("width", 77));
        if (scenario.equals("other-uploader")) video.put("uploaderId", "other-uploader");
        if (scenario.equals("null-uploader")) video.put("uploaderId", null);
        if (!scenario.equals("missing-video")) mongoTemplate.getCollection("video").insertOne(video);
        if (!scenario.equals("missing-file")) mongoTemplate.getCollection("file").insertOne(
                new Document("_id", "fixture-raw").append("key", "fixture/source.mp4"));
        if (scenario.equals("existing-lane")) mongoTemplate.getCollection("transcode").insertOne(
                new Document("_id", "tr_local_fixture-video_720p").append("resolution", "720p")
                        .append("m3u8Key", "fixture/old.m3u8").append("provider", "LOCAL")
                        .append("status", "FINISHED").append("actualWidth", 777));
        JSONObject body = JSON.parseObject("{\"videoId\":\"fixture-video\",\"resolution\":\"720p\","
                + "\"width\":1920,\"height\":1080,\"durationMs\":60000,\"videoCodec\":\"h264\","
                + "\"audioCodec\":\"aac\",\"bitrate\":738}");
        switch (scenario) {
            case "1080p" -> body.put("resolution", "1080p");
            case "invalid-resolution" -> body.put("resolution", "480p");
            case "null-resolution" -> body.put("resolution", null);
            case "missing-duration" -> body.remove("durationMs");
            case "zero-duration" -> body.put("durationMs", 0);
            case "null-width" -> body.put("width", null);
            case "negative-size" -> { body.put("width", -641); body.put("height", -481); }
            case "small" -> { body.put("width", 641); body.put("height", 481); }
            case "portrait" -> { body.put("width", 1080); body.put("height", 1920); }
            case "zero-size" -> { body.put("width", 0); body.put("height", 0); }
            case "display-size" -> { body.put("displayWidth", 1080); body.put("displayHeight", 1920); }
            case "float-rounding" -> { body.put("width", 1456539692); body.put("height", 1436145); }
            case "rich-media" -> {
                body.put("frameRate", 59.94); body.put("frameRateFraction", "60000/1001");
                body.put("frameRateMode", "VFR"); body.put("sar", "1:1"); body.put("rotation", 90);
                body.put("pixFmt", "yuv420p10le"); body.put("bitDepth", 10); body.put("dynamicRange", "HDR10");
                body.put("colorPrimaries", "bt2020"); body.put("colorTransfer", "smpte2084");
                body.put("colorSpace", "bt2020nc"); body.put("colorRange", "tv"); body.put("audioTrackCount", 2);
            }
            case "numeric-frame-rate" -> body.put("frameRate", 59.94);
            default -> { }
        }
        return switch (scenario) {
            case "empty-body" -> "";
            case "null-body" -> "null";
            case "array-body" -> "[]";
            default -> body.toJSONString();
        };
    }

    @Test
    void createLaneBindingPlanningMediaAndPartialWritesMatchOldSnapshots() throws Exception {
        JSONArray cases = new JSONArray();
        try {
            for (String scenario : List.of("success", "1080p", "small", "portrait", "display-size", "float-rounding",
                    "zero-size", "negative-size", "zero-duration", "rich-media", "numeric-frame-rate", "existing-media",
                    "existing-lane", "ready-video", "missing-file", "credentials-null", "credentials-failure",
                    "auto-mode", "invalid-resolution", "null-resolution", "missing-duration", "null-width",
                    "missing-video", "other-uploader", "null-uploader", "empty-body", "null-body", "array-body", "no-token")) {
                String input = seed(scenario);
                JSONObject row = new JSONObject(true);
                row.put("scenario", scenario); row.put("input", input); row.put("before", state());
                var request = post("/transcode/local/createTranscode").contentType("application/json").content(input);
                if (!scenario.equals("no-token")) request.header("token", "fixture-local-token");
                var response = MockMvcBuilders.webAppContextSetup(context).build().perform(request).andReturn().getResponse();
                row.put("status", response.getStatus()); row.put("contentType", response.getContentType());
                row.put("response", JSON.parseObject(response.getContentAsString())); row.put("after", state());
                row.put("requestLogs", mongoTemplate.getCollection("requestLog").countDocuments());
                row.put("exceptionLogs", mongoTemplate.getCollection("exceptionLog").countDocuments());
                row.put("remoteCalls", mockingDetails(ossVideoService).getInvocations().size());
                cases.add(row);
                verifyNoInteractions(aliyunMpsService, cloudFunctionTranscodeService, md5CfService);
            }
            String serialized = JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat);
            String output = System.getProperty("contract.local-transcode.output.dir");
            if (output != null) {
                Files.createDirectories(Path.of(output)); Files.writeString(Path.of(output, "cases.json"), serialized);
            } else {
                assertEquals(JSON.parseArray(Files.readString(Path.of("../../serverless/contracts/local-transcode/cases.json"))),
                        JSON.parseArray(serialized));
            }
        } finally {
            cleanDatabase();
        }
    }
}
