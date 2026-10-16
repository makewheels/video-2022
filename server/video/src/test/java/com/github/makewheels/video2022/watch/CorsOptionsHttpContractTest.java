package com.github.makewheels.video2022.watch;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** Real handler-mapping CORS and OPTIONS behavior; no production calls. */
class CorsOptionsHttpContractTest extends BaseIntegrationTest {
    @Autowired private WebApplicationContext context;

    @Test
    void headUsesGetHandlerAndJournalsOriginalMethod() throws Exception {
        cleanDatabase();
        try {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var get = mvc.perform(request(HttpMethod.GET, "/video/getPublicVideoList")
                    .param("skip", "0").param("limit", "20")).andReturn().getResponse();
            var head = mvc.perform(request(HttpMethod.HEAD, "/video/getPublicVideoList")
                    .param("skip", "0").param("limit", "20")).andReturn().getResponse();
            assertEquals(200, head.getStatus());
            assertEquals(get.getContentType(), head.getContentType());
            if (!head.getContentAsString().isEmpty()) assertEquals(get.getContentAsString(), head.getContentAsString());
            assertEquals(2, mongoTemplate.getCollection("requestLog").countDocuments());
            assertEquals(1, mongoTemplate.getCollection("requestLog")
                    .countDocuments(new org.bson.Document("request.method", "HEAD")));
            assertEquals(0, mongoTemplate.getCollection("exceptionLog").countDocuments());
        } finally {
            cleanDatabase();
        }
    }

    @Test
    void scopedCorsAndOptionsResponses() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        JSONArray cases = new JSONArray();
        try {
            for (String path : List.of("/client/requestClientId", "/playback/start", "/heartbeat/add", "/user/getMyProfile")) {
                for (String wanted : List.of("GET", "POST", "DELETE", "none")) {
                    cleanDatabase();
                    var input = request(HttpMethod.OPTIONS, path);
                    if (!wanted.equals("none")) {
                        input.header("Origin", "https://fixture.invalid");
                        input.header("Access-Control-Request-Method", wanted);
                        input.header("Access-Control-Request-Headers", "token, content-type");
                    }
                    var response = mvc.perform(input).andReturn().getResponse();
                    JSONObject row = new JSONObject(true);
                    row.put("path", path);
                    row.put("wanted", wanted);
                    row.put("status", response.getStatus());
                    row.put("body", response.getContentAsString());
                    JSONObject headers = new JSONObject(true);
                    for (String name : response.getHeaderNames()) {
                        if (!name.equalsIgnoreCase("Content-Length")) {
                            headers.put(name.toLowerCase(java.util.Locale.ROOT), response.getHeaders(name));
                        }
                    }
                    row.put("headers", headers);
                    row.put("requestLogs", mongoTemplate.getCollection("requestLog").countDocuments());
                    row.put("exceptionLogs", mongoTemplate.getCollection("exceptionLog").countDocuments());
                    row.put("clients", mongoTemplate.getCollection("client").countDocuments());
                    row.put("sessions", mongoTemplate.getCollection("playbackSession").countDocuments());
                    cases.add(row);
                }
            }
            cleanDatabase();
            var actual = mvc.perform(request(HttpMethod.GET, "/client/requestClientId")
                    .header("Origin", "https://fixture.invalid")).andReturn().getResponse();
            assertEquals("*", actual.getHeader("Access-Control-Allow-Origin"));
            assertEquals(List.of("Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"),
                    List.copyOf(actual.getHeaders("Vary")));
            assertEquals(1, mongoTemplate.getCollection("client").countDocuments());
            assertEquals(1, mongoTemplate.getCollection("requestLog").countDocuments());
            String serialized = JSON.toJSONString(cases, SerializerFeature.WriteMapNullValue,
                    SerializerFeature.PrettyFormat);
            String output = System.getProperty("contract.output.dir");
            if (output != null) {
                Path folder = Path.of(output);
                Files.createDirectories(folder);
                Files.writeString(folder.resolve("cases.json"), serialized);
            } else {
                assertEquals(JSON.parseArray(Files.readString(Path.of("../../serverless/contracts/cors-options/cases.json"))),
                        JSON.parseArray(serialized));
            }
        } finally {
            cleanDatabase();
        }
    }
}
