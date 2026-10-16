package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.video.bean.entity.Video;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

/** Existing status selection and precise Mongo update; no callback dispatch or media work. */
class VideoReadinessContractTest extends BaseIntegrationTest {
    @Autowired private TranscodeCallbackService callbacks;

    private JSONObject run(String scenario, String provider, String current, String laneStatus, String videoStatus)
            throws Exception {
        cleanDatabase();
        List<String> ids = new ArrayList<>(List.of("fixture-lane"));
        if (scenario.equals("empty")) ids.clear();
        if (scenario.equals("missing-reference")) ids.add("fixture-missing");
        if (scenario.equals("mixed")) ids.add("fixture-other");
        Document video = new Document("_id", "fixture-video").append("status", videoStatus)
                .append("transcodeIds", ids).append("updateTime", new Date(12345L))
                .append("unknownField", "preserved");
        mongoTemplate.getCollection("video").insertOne(video);
        Document lane = new Document("_id", "fixture-lane").append("provider", provider).append("status", laneStatus);
        if (current != null) lane.put("currentProvider", current);
        if (!scenario.equals("absent")) mongoTemplate.getCollection("transcode").insertOne(lane);
        if (scenario.equals("mixed")) {
            mongoTemplate.getCollection("transcode").insertOne(new Document("_id", "fixture-other")
                    .append("provider", "LOCAL_TRANSCODE").append("status", "TRANSCODING"));
        }
        JSONObject row = new JSONObject(true);
        row.put("scenario", scenario);
        row.put("before", video);
        row.put("lanes", mongoTemplate.getCollection("transcode").find().into(new ArrayList<>()));
        Video loaded = mongoTemplate.findById("fixture-video", Video.class);
        callbacks.updateVideoStatus(loaded);
        row.put("status", loaded.getStatus());
        Document after = mongoTemplate.getCollection("video").find().first();
        assertEquals(new Date(12345L), after.getDate("updateTime"));
        assertEquals("preserved", after.getString("unknownField"));
        row.put("after", after);
        assertEquals(0, mongoTemplate.getCollection("fcTask").countDocuments());
        verifyNoInteractions(ossVideoService, aliyunMpsService, cloudFunctionTranscodeService, md5CfService);
        return row;
    }

    @Test
    void allProvidersOverridesEmptyReferencesAndReadyMonotonicityMatchOldMongo() throws Exception {
        JSONArray cases = new JSONArray();
        try {
            for (String provider : Arrays.asList("LOCAL_TRANSCODE", "ALIYUN_MPS_TRANSCODE",
                    "ALIYUN_CLOUD_FUNCTION_TRANSCODE", "ALIYUN_CLOUD_FUNCTION_TRANSCODE_CPU",
                    "ALIYUN_CLOUD_FUNCTION_TRANSCODE_GPU", "unknown", null)) {
                for (String status : Arrays.asList("FINISHED", "FAILED", "TranscodeSuccess", "TranscodeFail", null)) {
                    cases.add(run("provider-status", provider, null, status, "CREATED"));
                }
            }
            for (String current : List.of("LOCAL_TRANSCODE", "ALIYUN_MPS_TRANSCODE", "unknown", "")) {
                cases.add(run("provider-override", "ALIYUN_MPS_TRANSCODE", current, "FINISHED", "TRANSCODING"));
            }
            for (String scenario : List.of("empty", "absent", "missing-reference", "mixed")) {
                cases.add(run(scenario, "LOCAL_TRANSCODE", null, "FINISHED", "TRANSCODING"));
            }
            cases.add(run("ready-keep", "LOCAL_TRANSCODE", null, "FAILED", "READY"));
            cases.add(run("no-change", "LOCAL_TRANSCODE", null, "FINISHED", "READY"));
            String serialized = JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat);
            String output = System.getProperty("contract.readiness.output.dir");
            if (output != null) {
                Files.createDirectories(Path.of(output));
                Files.writeString(Path.of(output, "cases.json"), serialized);
            } else {
                assertEquals(JSON.parseArray(Files.readString(Path.of("../../serverless/contracts/video-readiness/cases.json"))),
                        JSON.parseArray(serialized));
            }
        } finally {
            cleanDatabase();
        }
    }
}
