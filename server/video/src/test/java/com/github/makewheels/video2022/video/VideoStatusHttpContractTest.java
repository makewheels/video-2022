package com.github.makewheels.video2022.video;

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
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Legacy upload polling status, using real MVC, token lookups and isolated Mongo. */
class VideoStatusHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;

    @Test
    void pollingHeadOptionsAndUnsupportedMethodKeepExistingJournalBehavior() throws Exception {
        cleanDatabase();
        try {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mongoTemplate.getCollection("user").insertOne(new Document("_id", "fixture-uploader")
                    .append("token", "fixture-status-token"));
            mongoTemplate.getCollection("video").insertOne(new Document("_id", "fixture-video")
                    .append("uploaderId", "fixture-uploader").append("status", "READY"));
            assertEquals(401, mvc.perform(options("/video/getVideoStatus")).andReturn().getResponse().getStatus());
            var options = mvc.perform(options("/video/getVideoStatus").header("token", "fixture-status-token"))
                    .andReturn().getResponse();
            assertEquals(200, options.getStatus());
            assertEquals("GET,HEAD,OPTIONS", options.getHeader("Allow"));
            var head = mvc.perform(head("/video/getVideoStatus").param("videoId", "fixture-video")
                    .header("token", "fixture-status-token")).andReturn().getResponse();
            assertEquals(200, head.getStatus());
            assertEquals("application/json", head.getContentType());
            var post = mvc.perform(post("/video/getVideoStatus").param("videoId", "fixture-video")
                    .header("token", "fixture-status-token")).andReturn().getResponse();
            assertEquals(200, post.getStatus());
            assertEquals(1, JSON.parseObject(post.getContentAsString()).getIntValue("code"));
            assertEquals(3, mongoTemplate.getCollection("requestLog").countDocuments());
            assertEquals(1, mongoTemplate.getCollection("requestLog").countDocuments(new Document("request.method", "HEAD")));
            assertEquals(1, mongoTemplate.getCollection("exceptionLog").countDocuments());
        } finally {
            cleanDatabase();
        }
    }

    @Test
    void statusesOwnershipAndAuthenticationMatchCommittedSnapshots() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        JSONArray cases = new JSONArray();
        try {
            for (String scenario : Arrays.asList("CREATED", "UPLOADING", "PREPARE_TRANSCODING", "TRANSCODING",
                    "TRANSCODING_PARTLY_COMPLETE", "PROCESSING_AFTER_TRANSCODE_COMPLETE", "READY",
                    "TRANSCODE_FAILED", "ready", "unknown", "missing-status", "null-status",
                    "other-owner", "null-uploader", "missing-video", "missing-parameter", "no-token", "bad-token", "object-id")) {
                cleanDatabase();
                mongoTemplate.getCollection("user").insertOne(new Document("_id", "fixture-uploader")
                        .append("token", "fixture-status-token"));
                Document video = new Document("_id", scenario.equals("object-id")
                        ? new ObjectId("507f1f77bcf86cd799439011") : "fixture-video")
                        .append("uploaderId", "fixture-uploader").append("status", scenario);
                if (scenario.equals("missing-status")) video.remove("status");
                if (scenario.equals("null-status")) video.put("status", null);
                if (scenario.equals("other-owner")) {
                    video.put("uploaderId", "other-user");
                    video.put("ownerId", "fixture-uploader");
                }
                if (scenario.equals("null-uploader")) video.put("uploaderId", null);
                if (!scenario.equals("missing-video")) mongoTemplate.getCollection("video").insertOne(video);
                Document before = mongoTemplate.getCollection("video").find().first();
                var request = get("/video/getVideoStatus");
                if (!scenario.equals("missing-parameter")) request.param("videoId", video.get("_id").toString());
                if (!scenario.equals("no-token")) {
                    request.header("token", scenario.equals("bad-token") ? "invalid" : "fixture-status-token");
                }
                var response = mvc.perform(request).andReturn().getResponse();
                JSONObject row = new JSONObject(true);
                row.put("scenario", scenario);
                Document input = new Document(video);
                input.put("_id", video.get("_id").toString());
                row.put("video", scenario.equals("missing-video") ? null : input);
                row.put("status", response.getStatus());
                row.put("contentType", response.getContentType());
                row.put("response", JSON.parseObject(response.getContentAsString()));
                row.put("requestLogs", mongoTemplate.getCollection("requestLog").countDocuments());
                row.put("exceptionLogs", mongoTemplate.getCollection("exceptionLog").countDocuments());
                assertEquals(before, mongoTemplate.getCollection("video").find().first());
                assertEquals(0, mongoTemplate.getCollection("file").countDocuments());
                cases.add(row);
            }
            String serialized = JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat);
            String output = System.getProperty("contract.status.output.dir");
            if (output != null) {
                Path folder = Path.of(output);
                Files.createDirectories(folder);
                Files.writeString(folder.resolve("cases.json"), serialized);
            } else {
                assertEquals(JSON.parseArray(Files.readString(Path.of("../../serverless/contracts/video-status/cases.json"))),
                        JSON.parseArray(serialized));
            }
        } finally {
            cleanDatabase();
        }
    }
}
