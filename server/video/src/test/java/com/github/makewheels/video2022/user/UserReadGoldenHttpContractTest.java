package com.github.makewheels.video2022.user;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.user.bean.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Fixed synthetic Java HTTP oracle. Optional export is confined to a task-owned OS temp folder. */
class UserReadGoldenHttpContractTest extends BaseIntegrationTest {
    private static final String ID = "fixture-user-reads";
    private static final String TOKEN = "fixture-legacy-token";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        User user = new User();
        user.setId(ID);
        user.setToken(TOKEN);
        user.setPhone("00000000000");
        user.setRegisterChannel("TEST");
        user.setCreateTime(new Date(1704164645000L));
        user.setUpdateTime(new Date(1704164645678L));
        user.setNickname("测试读取");
        user.setAvatarUrl("https://example.invalid/avatar.png");
        user.setSubscriberCount(12L);
        user.setVideoCount(7L);
        mongoTemplate.save(user);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private JSONObject capture(String name, MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("application/json", result.getResponse().getContentType());
        JSONObject fixture = new JSONObject(true);
        fixture.put("status", result.getResponse().getStatus());
        fixture.put("contentType", result.getResponse().getContentType());
        fixture.put("body", JSON.parseObject(result.getResponse().getContentAsString()));
        String configured = System.getProperty("user.read.fixture.dir");
        if (configured != null) {
            Path directory = Path.of(configured).toRealPath();
            Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
            assertTrue(directory.startsWith(temporaryRoot));
            assertTrue(directory.getParent().getFileName().toString().startsWith("video-user-read-"));
            assertEquals("goldens", directory.getFileName().toString());
            String exported = JSON.toJSONString(fixture, SerializerFeature.WriteMapNullValue);
            assertEquals(fixture.getJSONObject("body"), JSON.parseObject(exported).getJSONObject("body"));
            Files.writeString(directory.resolve(name + ".json"), exported, StandardCharsets.UTF_8);
        }
        return fixture.getJSONObject("body");
    }

    @Test
    void tokenLookupKeepsLegacyCredentialAndCompleteFieldShape() throws Exception {
        JSONObject body = capture("token-found", get("/user/getUserByToken").param("token", TOKEN));
        assertEquals(0, body.getIntValue("code"));
        assertEquals("success", body.getString("message"));
        JSONObject user = body.getJSONObject("data");
        assertEquals(13, user.size());
        assertEquals(TOKEN, user.getString("token"));
        assertEquals("2024-01-02T03:04:05.000Z", user.getString("createTime"));
        assertEquals("2024-01-02T03:04:05.678Z", user.getString("updateTime"));
        assertTrue(user.containsKey("avatarFileId"));
        assertNull(user.get("avatarFileId"));
    }

    @Test
    void publicLookupRedactsOnlyReturnedToken() throws Exception {
        JSONObject body = capture("id-found", get("/user/getUserById").param("userId", ID));
        assertEquals(0, body.getIntValue("code"));
        assertNull(body.getJSONObject("data").get("token"));
        assertEquals(TOKEN, mongoTemplate.findById(ID, User.class).getToken());
    }

    @Test
    void missingTokenKeepsBusinessErrorAndNullData() throws Exception {
        JSONObject body = capture("token-missing", get("/user/getUserByToken").param("token", "fixture-missing"));
        assertEquals(13, body.getIntValue("code"));
        assertEquals("登陆token校验未通过", body.getString("message"));
        assertTrue(body.containsKey("data"));
        assertNull(body.get("data"));
    }

    @Test
    void missingPublicUserIsSuccessfulNullData() throws Exception {
        JSONObject body = capture("id-missing", get("/user/getUserById").param("userId", "fixture-missing"));
        assertEquals(0, body.getIntValue("code"));
        assertEquals("success", body.getString("message"));
        assertNull(body.get("data"));
    }
}
