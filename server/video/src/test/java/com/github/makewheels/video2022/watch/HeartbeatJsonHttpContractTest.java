package com.github.makewheels.video2022.watch;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.BaseIntegrationTest;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real MVC JSON binding snapshots; deterministic input and isolated Mongo only. */
class HeartbeatJsonHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;

    @Test
    void jsonBindingMatrixMatchesCommittedLegacySnapshots() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        JSONArray cases = new JSONArray();
        List<String> bodies = List.of(
                "{}", "null", "[]", "", "{} {}", "{\"unknown\":{\"nested\":[1]}}",
                "{\"videoId\":123,\"sessionId\":true,\"event\":1e2}",
                "{\"videoId\":[],\"videoId\":\"valid\"}", "{\"videoId\":null}",
                "{\"playerTime\":1.9}", "{\"playerTime\":-1.9}", "{\"playerTime\":\" 42 \"}",
                "{\"playerTime\":\"\"}", "{\"playerTime\":true}", "{\"playerTime\":\"1.9\"}",
                "{\"playerTime\":9223372036854775807}", "{\"playerTime\":9223372036854775808}",
                "{\"playerTime\":1,\"playerTime\":null}", "{\"playerTime\":{},\"playerTime\":1}",
                "{\"playerVolume\":0.1250}", "{\"playerVolume\":\" 1.2500 \"}",
                "{\"playerVolume\":\"\"}", "{\"playerVolume\":true}", "{\"playerVolume\":1e2}",
                "{\"clientTime\":1704164645000}", "{\"clientTime\":\"1704164645000\"}",
                "{\"clientTime\":\"2024-01-02T03:04:05.678Z\"}",
                "{\"clientTime\":\"2024-01-02T11:04:05.678+08:00\"}",
                "{\"clientTime\":\"2024-01-02\"}", "{\"clientTime\":\"\"}",
                "{\"clientTime\":1.5}", "{\"clientTime\":false}",
                "{\"createTime\":\"invalid\"}", "{\"createTime\":1}",
                "{\"playerTime\":1,\"playerTime\":2}", "{\"clientTime\":\"invalid\"}",
                "{\"playerTime\":\"null\"}", "{\"playerVolume\":\"null\"}",
                "{\"playerVolume\":\"1_000\"}", "{\"playerVolume\":\"NaN\"}",
                "{\"playerTime\":\"+42\"}", "{\"playerTime\":1e2}",
                "{\"clientTime\":-1}", "{\"clientTime\":\"null\"}",
                "{\"clientTime\":\"Tue, 02 Jan 2024 03:04:05 GMT\"}",
                "{\"clientTime\":\"2024-01-02T03:04:05\"}",
                "{\"clientTime\":\"2024-01-02T11:04:05+0800\"}",
                "{\"clientTime\":\"2024-01-02T03:04:05.123456789Z\"}",
                "{\"clientTime\":\"2024-02-30\"}", "\ufeff{}"
        );
        try {
            for (String body : bodies) {
                cleanDatabase();
                var response = mvc.perform(post("/heartbeat/add").contentType("application/json").content(body))
                        .andReturn().getResponse();
                JSONObject row = new JSONObject(true);
                row.put("input", body);
                row.put("status", response.getStatus());
                row.put("contentType", response.getContentType());
                row.put("response", JSON.parseObject(response.getContentAsString()));
                Document saved = mongoTemplate.getCollection("heartbeat").find().first();
                JSONObject normalized = null;
                if (saved != null) {
                    saved.remove("_id");
                    assertNotNull(saved.remove("createTime"));
                    normalized = new JSONObject(true);
                    for (var entry : saved.entrySet()) {
                        Object value = entry.getValue();
                        if (value instanceof Date date) value = date.getTime();
                        if (value instanceof Decimal128 decimal) value = decimal.bigDecimalValue().toString();
                        normalized.put(entry.getKey(), value);
                    }
                }
                row.put("saved", normalized);
                cases.add(row);
            }
            String output = System.getProperty("contract.output.dir");
            if (output != null) {
                Path folder = Path.of(output);
                Files.createDirectories(folder);
                Files.writeString(folder.resolve("cases.json"),
                        JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat));
            } else {
                Path fixture = Path.of("../../serverless/contracts/heartbeat-json/cases.json");
                assertEquals(JSON.parseArray(Files.readString(fixture)),
                        JSON.parseArray(JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue)));
            }
        } finally {
            cleanDatabase();
        }
    }
}
