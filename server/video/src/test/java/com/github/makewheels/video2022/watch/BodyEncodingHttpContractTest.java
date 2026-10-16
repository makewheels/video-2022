package com.github.makewheels.video2022.watch;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.BaseIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Byte-level legacy converter behavior, never inferred from the declared charset. */
class BodyEncodingHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;

    private JSONObject snapshot(MockMvc mvc, String path, byte[] bytes, String media) throws Exception {
        cleanDatabase();
        mongoTemplate.getCollection("user").insertOne(new Document("_id", "fixture-user")
                .append("token", "fixture-token").append("nickname", "before").append("bio", "before"));
        if (path.endsWith("/exit")) {
            mongoTemplate.getCollection("playbackSession").insertOne(new Document("_id", "fixture-session")
                    .append("exitType", "PLAYING").append("heartbeatCount", 0));
        }
        var response = mvc.perform(post(path).contentType(media).content(bytes).header("token", "fixture-token"))
                .andReturn().getResponse();
        JSONObject body = JSON.parseObject(response.getContentAsString());
        if (path.endsWith("/start") && body.getIntValue("code") == 0) {
            body.getJSONObject("data").put("playbackSessionId", "generated");
        }
        String collection = path.startsWith("/user/") ? "user" : path.startsWith("/heartbeat/")
                ? "heartbeat" : "playbackSession";
        Document document = mongoTemplate.getCollection(collection).find().first();
        JSONObject saved = null;
        if (document != null) {
            saved = new JSONObject(true);
            List<String> names = path.startsWith("/user/") ? List.of("nickname", "bio")
                    : path.startsWith("/heartbeat/") ? List.of("event", "clientId", "viewerId")
                    : path.endsWith("/start") ? List.of("videoId", "clientId") : List.of("exitType");
            for (String name : names) saved.put(name, document.get(name));
            if (path.endsWith("/exit")) saved.put("ended", document.containsKey("endTime"));
        }
        JSONObject row = new JSONObject(true);
        row.put("path", path);
        row.put("bytes", Base64.getEncoder().encodeToString(bytes));
        row.put("media", media);
        row.put("status", response.getStatus());
        row.put("contentType", response.getContentType());
        row.put("body", body);
        row.put("saved", saved);
        row.put("requestLogs", mongoTemplate.getCollection("requestLog").countDocuments());
        row.put("exceptionLogs", mongoTemplate.getCollection("exceptionLog").countDocuments());
        return row;
    }

    @Test
    void jsonStreamAndStringConvertersRespectTheirOwnEncodingRules() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        JSONArray cases = new JSONArray();
        try {
            for (String path : List.of("/heartbeat/add", "/user/updateProfile", "/playback/start", "/playback/exit")) {
                String body = path.startsWith("/heartbeat/") ? "{\"clientId\":\"c\",\"event\":\"编码-café\"}"
                        : path.startsWith("/user/") ? "{\"nickname\":\"编码-café\",\"bio\":\"fixture\"}"
                        : path.endsWith("/start") ? "{\"videoId\":\"编码-café\",\"clientId\":\"c\"}"
                        : "{\"playbackSessionId\":\"fixture-session\",\"exitType\":\"编码-café\"}";
                for (String charset : List.of("UTF-8", "UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE", "ISO-8859-1")) {
                    for (boolean bom : List.of(false, true)) {
                        if (bom && charset.equals("ISO-8859-1")) continue;
                        String hex = switch (charset) {
                            case "UTF-8" -> "efbbbf";
                            case "UTF-16LE" -> "fffe";
                            case "UTF-16BE" -> "feff";
                            case "UTF-32LE" -> "fffe0000";
                            case "UTF-32BE" -> "0000feff";
                            default -> "";
                        };
                        byte[] data = body.getBytes(Charset.forName(charset));
                        byte[] prefix = bom ? HexFormat.of().parseHex(hex) : new byte[0];
                        byte[] bytes = new byte[prefix.length + data.length];
                        System.arraycopy(prefix, 0, bytes, 0, prefix.length);
                        System.arraycopy(data, 0, bytes, prefix.length, data.length);
                        cases.add(snapshot(mvc, path, bytes, "application/json"));
                        cases.add(snapshot(mvc, path, bytes, "application/json;charset=" + charset));
                    }
                }
                byte[] broken = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                for (int index = 0; index < broken.length; index++) {
                    if (broken[index] < 0) { broken[index] = (byte) 0xff; break; }
                }
                cases.add(snapshot(mvc, path, broken, "application/json"));
            }
            String serialized = JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue,
                    SerializerFeature.PrettyFormat);
            String output = System.getProperty("contract.output.dir");
            if (output != null) {
                Path folder = Path.of(output);
                Files.createDirectories(folder);
                Files.writeString(folder.resolve("cases.json"), serialized);
            } else {
                assertEquals(JSON.parseArray(Files.readString(Path.of("../../serverless/contracts/body-encoding/cases.json"))),
                        JSON.parseArray(serialized));
            }
        } finally {
            cleanDatabase();
        }
    }
}
