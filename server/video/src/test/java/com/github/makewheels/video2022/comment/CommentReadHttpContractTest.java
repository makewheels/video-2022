package com.github.makewheels.video2022.comment;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Actual legacy public comment reads and Mongo pagination, with synthetic content only. */
class CommentReadHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;

    private void seed(String scenario) {
        cleanDatabase();
        if (!scenario.equals("missing-video")) {
            mongoTemplate.getCollection("video").insertOne(new Document("_id", "fixture-video")
                    .append("visibility", "PRIVATE").append("commentCount", 999));
        }
        if (scenario.equals("empty")) return;
        var comments = mongoTemplate.getCollection("comment");
        comments.insertOne(new Document("_id", "fixture-top-a").append("videoId", "fixture-video")
                .append("content", "fixture top a").append("likeCount", 10).append("replyCount", 1)
                .append("createTime", new Date(1000L)).append("updateTime", new Date(1001L))
                .append("userId", "fixture-user").append("userPhone", "00000000000")
                .append("userNickname", "fixture-name").append("userAvatarUrl", "https://example.invalid/avatar")
                .append("unknownField", "not-mapped"));
        comments.insertOne(new Document("_id", "fixture-top-b").append("videoId", "fixture-video")
                .append("parentId", null).append("content", "fixture top b").append("likeCount", 1)
                .append("createTime", new Date(2000L)));
        comments.insertOne(new Document("_id", "fixture-empty-parent").append("videoId", "fixture-video")
                .append("parentId", "").append("content", "fixture empty parent").append("createTime", new Date(3000L)));
        comments.insertOne(new Document("_id", "fixture-reply").append("videoId", "fixture-video")
                .append("parentId", "fixture-top-a").append("content", "fixture reply")
                .append("replyToUserId", "fixture-user").append("replyToUserPhone", "00000000000")
                .append("replyToUserNickname", "fixture-name").append("createTime", new Date(4000L)));
        comments.insertOne(new Document("_id", "fixture-other-video").append("videoId", "other-video")
                .append("content", "fixture other").append("createTime", new Date(5000L)));
    }

    @Test
    void publicListRepliesAndCountMatchOldHttpAndDoNotWriteBusinessData() throws Exception {
        JSONArray cases = new JSONArray();
        try {
            for (String operation : List.of("getByVideoId", "getReplies", "getCount")) {
                for (String scenario : List.of("default", "empty", "missing-video", "missing-parameter",
                        "empty-parameter", "first-page", "negative-page", "zero-size", "negative-size", "large-size",
                        "overflow-page", "invalid-page", "hex-page", "hot", "unknown-sort", "invalid-token")) {
                    seed(scenario);
                    JSONObject parameters = new JSONObject(true);
                    String parameter = operation.equals("getReplies") ? "parentId" : "videoId";
                    if (!scenario.equals("missing-parameter")) parameters.put(parameter,
                            scenario.equals("empty-parameter") ? "" : operation.equals("getReplies") ? "fixture-top-a" : "fixture-video");
                    switch (scenario) {
                        case "first-page" -> { parameters.put("page", "1"); parameters.put("pageSize", "1"); }
                        case "negative-page" -> { parameters.put("page", "-1"); parameters.put("skip", "-1"); }
                        case "zero-size" -> { parameters.put("pageSize", "0"); parameters.put("limit", "0"); }
                        case "negative-size" -> { parameters.put("pageSize", "-1"); parameters.put("limit", "-1"); }
                        case "large-size" -> { parameters.put("pageSize", "100"); parameters.put("limit", "100"); }
                        case "overflow-page" -> { parameters.put("page", "2147483647"); parameters.put("pageSize", "50"); }
                        case "invalid-page" -> { parameters.put("page", "x"); parameters.put("skip", "x"); }
                        case "hex-page" -> { parameters.put("page", "0x1"); parameters.put("pageSize", "1"); }
                        case "hot" -> parameters.put("sortBy", "likeCount");
                        case "unknown-sort" -> parameters.put("sortBy", "hot");
                        default -> { }
                    }
                    var request = get("/comment/" + operation);
                    parameters.forEach((name, value) -> request.param(name, value.toString()));
                    if (scenario.equals("invalid-token")) request.header("token", "fixture-invalid-token");
                    var beforeComments = mongoTemplate.getCollection("comment").find().sort(new Document("_id", 1))
                            .into(new ArrayList<>());
                    var beforeVideo = mongoTemplate.getCollection("video").find().first();
                    var response = MockMvcBuilders.webAppContextSetup(context).build().perform(request).andReturn().getResponse();
                    JSONObject row = new JSONObject(true);
                    row.put("operation", operation);
                    row.put("scenario", scenario);
                    row.put("parameters", parameters);
                    row.put("comments", beforeComments);
                    row.put("video", beforeVideo);
                    row.put("status", response.getStatus());
                    row.put("contentType", response.getContentType());
                    row.put("response", JSON.parseObject(response.getContentAsString()));
                    row.put("requestLogs", mongoTemplate.getCollection("requestLog").countDocuments());
                    row.put("exceptionLogs", mongoTemplate.getCollection("exceptionLog").countDocuments());
                    assertEquals(beforeComments, mongoTemplate.getCollection("comment").find().sort(new Document("_id", 1))
                            .into(new ArrayList<>()));
                    assertEquals(beforeVideo, mongoTemplate.getCollection("video").find().first());
                    verifyNoInteractions(ossVideoService, aliyunMpsService, cloudFunctionTranscodeService, md5CfService);
                    cases.add(row);
                }
            }
            String serialized = JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat);
            String output = System.getProperty("contract.comment-read.output.dir");
            if (output != null) {
                Files.createDirectories(Path.of(output));
                Files.writeString(Path.of(output, "cases.json"), serialized);
            } else {
                assertEquals(JSON.parseArray(Files.readString(Path.of("../../serverless/contracts/comment-read/cases.json"))),
                        JSON.parseArray(serialized));
            }
        } finally {
            cleanDatabase();
        }
    }
}
