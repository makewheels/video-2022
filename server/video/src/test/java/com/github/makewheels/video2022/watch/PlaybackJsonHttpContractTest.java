package com.github.makewheels.video2022.watch;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.BaseIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** Compare Spring DTO binding and Fastjson String-body binding through the actual HTTP path. */
class PlaybackJsonHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;

    private JSONObject snapshot(MockMvc mvc, String route, String body, String media, String method) throws Exception {
        cleanDatabase();
        if (!route.equals("start")) {
            mongoTemplate.getCollection("playbackSession").insertOne(new Document("_id", "fixture-session")
                    .append("videoId", "fixture-video").append("heartbeatCount", 0)
                    .append("totalPlayDurationMs", 0L).append("maxProgressMs", 0L)
                    .append("currentProgressMs", 0L).append("resolution", "auto")
                    .append("exitType", "PLAYING").append("startTime", new Date(0))
                    .append("createTime", new Date(0)).append("updateTime", new Date(0)));
        }
        var response = mvc.perform(request(HttpMethod.valueOf(method), "/playback/" + route)
                        .contentType(media).content(body)).andReturn().getResponse();
        JSONObject row = new JSONObject(true);
        row.put("route", route);
        row.put("input", body);
        row.put("media", media);
        row.put("method", method);
        row.put("status", response.getStatus());
        row.put("contentType", response.getContentType());
        JSONObject responseBody = JSON.parseObject(response.getContentAsString());
        if (route.equals("start") && responseBody.getIntValue("code") == 0) {
            assertNotNull(responseBody.getJSONObject("data").getString("playbackSessionId"));
            responseBody.getJSONObject("data").put("playbackSessionId", "generated");
        }
        row.put("response", responseBody);
        Document saved = mongoTemplate.getCollection("playbackSession").find().first();
        if (saved != null) {
            saved.remove("_id");
            for (var entry : saved.entrySet()) {
                if (entry.getValue() instanceof Date) entry.setValue("present");
            }
        }
        row.put("saved", saved);
        row.put("requestLogs", mongoTemplate.getCollection("requestLog").countDocuments());
        row.put("exceptionLogs", mongoTemplate.getCollection("exceptionLog").countDocuments());
        return row;
    }

    @Test
    void separateJsonBindingAndExitPlainTextMatrix() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        JSONArray cases = new JSONArray();
        List<String> bodies = List.of(
                "{}", "null", "[]", "", "{} {}", "{\"unknown\":{\"nested\":[1]}}",
                "{\"videoId\":123,\"sessionId\":true,\"watchId\":1e2}",
                "{\"videoId\":[],\"videoId\":\"valid\"}",
                "{\"playbackSessionId\":\"fixture-session\",\"currentTimeMs\":1.9}",
                "{\"playbackSessionId\":\"fixture-session\",\"currentTimeMs\":true}",
                "{\"playbackSessionId\":\"fixture-session\",\"currentTimeMs\":\"42\"}",
                "{\"playbackSessionId\":\"fixture-session\",\"currentTimeMs\":\"\"}",
                "{\"playbackSessionId\":\"fixture-session\",\"currentTimeMs\":\"null\"}",
                "{\"playbackSessionId\":\"fixture-session\",\"currentTimeMs\":9223372036854775808}",
                "{\"playbackSessionId\":\"fixture-session\",\"currentTimeMs\":{},\"currentTimeMs\":1}",
                "{\"playbackSessionId\":\"fixture-session\",\"resolution\":true,\"exitType\":123}",
                "{\"playbackSessionId\":\"fixture-session\",\"isPlaying\":\"true\"}",
                "{\"playbackSessionId\":\"fixture-session\",\"isPlaying\":1}",
                "{\"playbackSessionId\":\"fixture-session\",\"isPlaying\":\"bad\"}",
                "{\"playbackSessionId\":\"fixture-session\",\"isPlaying\":{}}",
                "{playbackSessionId:'fixture-session',currentTimeMs:12,exitType:'CLOSE'}",
                "{\"playbackSessionId\":\"fixture-session\",,\"currentTimeMs\":12,}",
                "{/*fixture*/\"playbackSessionId\":\"fixture-session\",\"currentTimeMs\":12}",
                "{\"PLAYBACKSESSIONID\":\"fixture-session\",\"current_time_ms\":12}",
                "{\"playbackSessionId\":\"fixture-session\",\"currentTimeMs\":-1.9,"
                        + "\"totalPlayDurationMs\":-7,\"exitType\":\"custom\"}",
                "{\"playbackSessionId\":\"absent\"}",
                "{\"playbackSessionId\":\"fixture-session\",\"exitType\":\"退出\",\"resolution\":\"中文\"}",
                "{\"playbackSessionId\":\"fixture-session\",\"resolution\":1e2}",
                "{\"playbackSessionId\":\"fixture-session\",\"resolution\":{}}",
                "{\"playbackSessionId\":\"fixture-session\",\"resolution\":[]}"
        );
        try {
            for (String route : List.of("start", "heartbeat", "exit")) {
                for (String body : bodies) {
                    String media = route.equals("exit") ? "text/plain" : "application/json";
                    cases.add(snapshot(mvc, route, body, media, "POST"));
                }
                String body = "{\"playbackSessionId\":\"fixture-session\",\"currentTimeMs\":12}";
                cases.add(snapshot(mvc, route, body, "application/json", "GET"));
                cases.add(snapshot(mvc, route, body, "application/json", "PATCH"));
                cases.add(snapshot(mvc, route, body, "text/plain", "POST"));
                cases.add(snapshot(mvc, route, body, "application/problem+json", "POST"));
                cases.add(snapshot(mvc, route, body, "application/json", "POST"));
                cases.add(snapshot(mvc, route,
                        "{\"playbackSessionId\":\"fixture-session\",\"exitType\":\"退出\",\"resolution\":\"中文\"}",
                        "text/plain;charset=UTF-8", "POST"));
            }
            String serialized = JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue,
                    SerializerFeature.PrettyFormat);
            String output = System.getProperty("contract.output.dir");
            if (output != null) {
                Path folder = Path.of(output);
                Files.createDirectories(folder);
                Files.writeString(folder.resolve("cases.json"), serialized);
            } else {
                assertEquals(JSON.parseArray(Files.readString(Path.of("../../serverless/contracts/playback-json/cases.json"))),
                        JSON.parseArray(serialized));
            }
        } finally {
            cleanDatabase();
        }
    }
}
