package com.github.makewheels.video2022.cover;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.utils.IdService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

/** Actual old HTTP and BSON; synthetic identifiers/STS and no media processing. */
class LocalCoverHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;
    @MockitoBean private IdService ids;

    private Document normalized(Document document) {
        if (document == null) return null;
        for (String name : List.of("createTime", "updateTime", "finishTime")) {
            if (document.get(name) != null) {
                assertInstanceOf(Date.class, document.get(name));
                document.put(name, "<server-date>");
            }
        }
        return document;
    }

    private JSONObject state() {
        JSONObject state = new JSONObject(true);
        for (String name : List.of("video", "file", "cover")) {
            state.put(name, normalized(mongoTemplate.getCollection(name).find().first()));
        }
        return state;
    }

    private void seed(String scenario, boolean finish) {
        cleanDatabase();
        reset(ossVideoService, ids);
        when(ids.getFileId()).thenReturn("fixture-cover-file");
        when(ids.getCoverId()).thenReturn("fixture-cover");
        JSONObject credentials = JSON.parseObject("{\"bucket\":\"fixture-bucket\",\"key\":\"fixture-key\","
                + "\"endpoint\":\"https://example.invalid\",\"accessKeyId\":\"fixture-not-real\","
                + "\"secretKey\":\"fixture-not-real\",\"sessionToken\":\"fixture-not-real\","
                + "\"expiration\":\"2030-01-01T00:00:00Z\"}");
        when(ossVideoService.generateUploadCredentials(anyString())).thenReturn(credentials);
        if (scenario.equals("credentials-null")) {
            doReturn(null).when(ossVideoService).generateUploadCredentials(anyString());
        }
        if (scenario.equals("credentials-failure")) {
            doThrow(new IllegalStateException("synthetic STS failure"))
                    .when(ossVideoService).generateUploadCredentials(anyString());
        }
        mongoTemplate.getCollection("user").insertOne(new Document("_id", "fixture-uploader")
                .append("token", "fixture-local-token"));
        if (!scenario.equals("missing-video")) {
            Document video = new Document("_id", "fixture-video").append("uploaderId", "fixture-uploader")
                    .append("ownerId", "other-owner").append("videoType", "USER_UPLOAD")
                    .append("transcodeMode", scenario.equals("auto-mode") ? "AUTO" : "LOCAL")
                    .append("status", "READY").append("createTime", new Date(1705276800000L));
            if (scenario.equals("other-uploader")) video.put("uploaderId", "other-uploader");
            if (scenario.equals("null-uploader")) video.put("uploaderId", null);
            if (scenario.equals("rich-video")) {
                video.put("unknownField", "not-mapped");
                video.put("mediaInfo", new Document("duration", 1000L).append("frameRate", "60000/1001")
                        .append("response", new Document("keepNull", null)).append("unknownField", "not-mapped"));
                video.put("watch", new Document("watchCount", 5).append("showUploadTime", null)
                        .append("unknownField", "not-mapped"));
                video.put("youTube", new Document("videoInfo", new Document("keepNull", null)));
            }
            mongoTemplate.getCollection("video").insertOne(video);
        }
        if (finish && !scenario.equals("missing-cover")) {
            Document cover = new Document("_id", "fixture-cover").append("videoId", "fixture-video")
                    .append("fileId", "fixture-cover-file").append("status", "CREATED")
                    .append("provider", scenario.equals("other-provider") ? "ALIYUN_MPS_SNAPSHOT" : "LOCAL_COVER");
            if (scenario.equals("rich-video")) cover.put("result", new Document("keepNull", null));
            mongoTemplate.getCollection("cover").insertOne(cover);
            if (!scenario.equals("missing-file")) {
                mongoTemplate.getCollection("file").insertOne(new Document("_id", "fixture-cover-file")
                        .append("fileStatus", "CREATED").append("videoId", "fixture-video"));
            }
        }
    }

    @Test
    void unsupportedMethodSkipsIdentityAndJournalWhileOptionsRemainsProtected() throws Exception {
        try {
            seed("success", false);
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var unsupported = mvc.perform(get("/cover/local/createCover")).andReturn().getResponse();
            assertEquals(200, unsupported.getStatus());
            assertEquals(1, JSON.parseObject(unsupported.getContentAsString()).getIntValue("code"));
            assertEquals(0, mongoTemplate.getCollection("requestLog").countDocuments());
            assertEquals(1, mongoTemplate.getCollection("exceptionLog").countDocuments());
            assertEquals(401, mvc.perform(options("/cover/local/createCover")).andReturn().getResponse().getStatus());
            var allowed = mvc.perform(options("/cover/local/createCover").header("token", "fixture-local-token"))
                    .andReturn().getResponse();
            assertEquals(200, allowed.getStatus());
            assertEquals("POST,OPTIONS", allowed.getHeader("Allow"));
            assertEquals(1, mongoTemplate.getCollection("requestLog").countDocuments());
        } finally {
            cleanDatabase();
        }
    }

    @Test
    void creationAndFinishMatchCompleteOldHttpAndDocuments() throws Exception {
        JSONArray cases = new JSONArray();
        try {
            for (boolean finish : List.of(false, true)) {
                List<String> scenarios = finish
                        ? List.of("success", "rich-video", "other-provider", "missing-file", "missing-cover", "missing-video",
                                  "other-uploader", "null-uploader", "missing-parameter", "no-token")
                        : List.of("success", "auto-mode", "missing-video-id", "empty-extension", "blank-extension", "custom-extension",
                                  "credentials-null", "credentials-failure", "missing-video", "other-uploader",
                                  "null-uploader", "empty-body", "null-body", "array-body", "no-token");
                for (String scenario : scenarios) {
                    seed(scenario, finish);
                    var mvc = MockMvcBuilders.webAppContextSetup(context).build();
                    String body = switch (scenario) {
                        case "empty-body" -> "";
                        case "null-body" -> "null";
                        case "array-body" -> "[]";
                        case "missing-video-id" -> "{}";
                        case "empty-extension" -> "{\"videoId\":\"fixture-video\",\"extension\":\"\"}";
                        case "blank-extension" -> "{\"videoId\":\"fixture-video\",\"extension\":\"  \"}";
                        case "custom-extension" -> "{\"videoId\":\"fixture-video\",\"extension\":\"PNG\"}";
                        default -> "{\"videoId\":\"fixture-video\"}";
                    };
                    var request = post(finish ? "/cover/local/finishCover" : "/cover/local/createCover");
                    if (!scenario.equals("no-token")) request.header("token", "fixture-local-token");
                    if (finish && !scenario.equals("missing-parameter")) request.param("coverId", "fixture-cover");
                    if (!finish) request.contentType("application/json").content(body);
                    JSONObject row = new JSONObject(true);
                    row.put("operation", finish ? "finish" : "create");
                    row.put("scenario", scenario);
                    row.put("input", body);
                    row.put("before", state());
                    var response = mvc.perform(request).andReturn().getResponse();
                    row.put("status", response.getStatus());
                    row.put("contentType", response.getContentType());
                    row.put("response", JSON.parseObject(response.getContentAsString()));
                    row.put("after", state());
                    row.put("requestLogs", mongoTemplate.getCollection("requestLog").countDocuments());
                    row.put("exceptionLogs", mongoTemplate.getCollection("exceptionLog").countDocuments());
                    row.put("remoteCalls", mockingDetails(ossVideoService).getInvocations().size());
                    cases.add(row);
                    verifyNoInteractions(aliyunMpsService, cloudFunctionTranscodeService, md5CfService);
                }
            }
            String output = System.getProperty("contract.local-cover.output.dir");
            String serialized = JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat);
            if (output != null) {
                Files.createDirectories(Path.of(output));
                Files.writeString(Path.of(output, "cases.json"), serialized);
            } else {
                assertEquals(JSON.parseArray(Files.readString(Path.of("../../serverless/contracts/local-cover/cases.json"))),
                        JSON.parseArray(serialized));
            }
        } finally {
            cleanDatabase();
        }
    }
}
