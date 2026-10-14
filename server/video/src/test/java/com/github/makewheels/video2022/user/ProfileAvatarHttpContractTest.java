package com.github.makewheels.video2022.user;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.user.bean.User;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Legacy HTTP + real disposable Mongo; all OSS operations are mocked. */
class ProfileAvatarHttpContractTest extends BaseIntegrationTest {
    private static final String USER_ID = "fixture-profile-user";
    private static final String TOKEN = "fixture-profile-token";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        reset(ossVideoService);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        User user = new User();
        user.setId(USER_ID);
        user.setToken(TOKEN);
        user.setNickname("原昵称");
        user.setBio("原简介");
        user.setAvatarUrl("https://example.invalid/legacy-avatar.png");
        user.setUpdateTime(new Date(0));
        mongoTemplate.save(user);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private JSONObject body(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertEquals(status, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentType().startsWith("application/json"));
        return JSON.parseObject(result.getResponse().getContentAsString());
    }

    private JSONObject update(String nickname, String bio) throws Exception {
        JSONObject request = new JSONObject();
        request.put("nickname", nickname);
        request.put("bio", bio);
        return body(post("/user/updateProfile").header("token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(request.toJSONString()), 200);
    }

    private User stored() {
        return mongoTemplate.findById(USER_ID, User.class);
    }

    private File file(String id, String type, String owner) {
        File file = new File();
        file.setId(id);
        file.setFileType(type);
        file.setUploaderId(owner);
        file.setKey("avatar/" + owner + "/" + id);
        mongoTemplate.save(file);
        return file;
    }

    @Test
    void profileUsesUtf16LengthNoTrimAndMissingFieldsClearStoredValues() throws Exception {
        assertEquals(401, body(post("/user/updateProfile").contentType(MediaType.APPLICATION_JSON)
                .content("{}"), 401).getIntValue("code"));
        String astral = "\uD83D\uDE00";
        assertEquals(0, update(astral.repeat(15), " ").getIntValue("code"));
        assertEquals(astral.repeat(15), stored().getNickname());
        assertEquals(" ", stored().getBio());
        Date accepted = stored().getUpdateTime();
        assertTrue(accepted.getTime() > 0);
        assertEquals("昵称不能超过30字", update(astral.repeat(16), "new").getString("message"));
        assertEquals("简介不能超过200字", update("new", "字".repeat(201)).getString("message"));
        assertEquals(accepted, stored().getUpdateTime());
        JSONObject cleared = body(post("/user/updateProfile").header("token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{}"), 200);
        assertEquals(0, cleared.getIntValue("code"));
        assertNull(stored().getNickname());
        assertNull(stored().getBio());
        assertEquals(TOKEN, stored().getToken());
    }

    @Test
    void jsonScalarFieldsCoerceToStringsAndUnknownPropertiesAreIgnored() throws Exception {
        JSONObject result = body(post("/user/updateProfile").header("token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":123,\"bio\":true,\"ignored\":{\"nested\":1}}"), 200);
        assertEquals(0, result.getIntValue("code"));
        assertEquals("123", stored().getNickname());
        assertEquals("true", stored().getBio());
        assertEquals(TOKEN, stored().getToken());
    }

    @Test
    void jsonNumericLexemesAndDuplicatePropertiesRetainLegacyBinding() throws Exception {
        for (String[] values : new String[][]{{"1e2", "-0.50"}, {"1E+2", "-0"}}) {
            JSONObject result = body(post("/user/updateProfile").header("token", TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nickname\":" + values[0] + ",\"bio\":" + values[1] + "}"), 200);
            assertEquals(0, result.getIntValue("code"));
            assertEquals(values[0], stored().getNickname());
            assertEquals(values[1], stored().getBio());
        }
        JSONObject result = body(post("/user/updateProfile").header("token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + "x".repeat(31) + "\",\"nickname\":\"last\"}"), 200);
        assertEquals(0, result.getIntValue("code"));
        assertEquals("last", stored().getNickname());
        assertNull(stored().getBio());
    }

    @Test
    void jsonBindingRejectsTrailingRootOrGarbageBeforeProfileWrite() throws Exception {
        for (String json : new String[]{"{\"nickname\":\"first\"}{}", "{\"nickname\":\"first\"} trailing"}) {
            JSONObject result = body(post("/user/updateProfile").header("token", TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(json), 200);
            assertEquals(1, result.getIntValue("code"), json);
            assertEquals("原昵称", stored().getNickname());
            assertEquals("原简介", stored().getBio());
            assertEquals(new Date(0), stored().getUpdateTime());
        }
    }

    @Test
    void jsonContainersAndMissingBodiesFailBeforeAnyProfileWrite() throws Exception {
        for (String json : new String[]{"{\"nickname\":[]}", "{\"bio\":{}}",
                "{\"nickname\":[],\"nickname\":\"valid\"}",
                "[]", "\"text\"", "null", "", "{"}) {
            JSONObject result = body(post("/user/updateProfile").header("token", TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(json), 200);
            assertEquals(1, result.getIntValue("code"), json);
            assertEquals("未知错误", result.getString("message"), json);
            assertEquals("原昵称", stored().getNickname(), json);
            assertEquals("原简介", stored().getBio(), json);
            assertEquals(new Date(0), stored().getUpdateTime(), json);
        }
    }

    @Test
    void channelAndMyProfileSignAvatarFor24HoursWithoutPersistingResponseChanges() throws Exception {
        User user = stored();
        user.setAvatarFileId("fixture-avatar");
        user.setSubscriberCount(null);
        user.setVideoCount(null);
        mongoTemplate.save(user);
        // Save may omit null properties; force actual BSON null for this legacy-data case.
        mongoTemplate.getCollection("user").updateOne(new Document("_id", USER_ID),
                new Document("$set", new Document("subscriberCount", null).append("videoCount", null)));
        File avatar = file("fixture-avatar", FileType.AVATAR, "fixture-other-owner");
        when(ossVideoService.generatePresignedUrl(avatar.getKey(), Duration.ofHours(24)))
                .thenReturn("https://example.invalid/signed-avatar.png");
        JSONObject channel = body(get("/user/getChannel").param("userId", USER_ID), 200).getJSONObject("data");
        assertEquals("https://example.invalid/signed-avatar.png", channel.getString("avatarUrl"));
        assertEquals(0L, channel.getLongValue("subscriberCount"));
        assertEquals(0L, channel.getLongValue("videoCount"));
        assertFalse(channel.getBooleanValue("isSubscribed"));
        assertEquals(8, channel.size());
        JSONObject profile = body(get("/user/getMyProfile").header("token", TOKEN), 200).getJSONObject("data");
        assertNull(profile.get("token"));
        assertNull(profile.get("subscriberCount"));
        assertEquals("https://example.invalid/signed-avatar.png", profile.getString("avatarUrl"));
        assertEquals(TOKEN, stored().getToken());
        assertEquals("https://example.invalid/legacy-avatar.png", stored().getAvatarUrl());
        mongoTemplate.remove(avatar);
        JSONObject fallback = body(get("/user/getChannel").param("userId", USER_ID), 200).getJSONObject("data");
        assertEquals(stored().getAvatarUrl(), fallback.getString("avatarUrl"));
        assertEquals("用户不存在", body(get("/user/getChannel").param("userId", "fixture-missing"), 200)
                .getString("message"));
        verify(ossVideoService, times(2)).generatePresignedUrl(avatar.getKey(), Duration.ofHours(24));
    }

    @Test
    void eachAvatarCreationPersistsANewOwnedFileWithoutChangingProfile() throws Exception {
        JSONObject first = body(get("/user/createAvatarFile").header("token", TOKEN), 200).getJSONObject("data");
        JSONObject second = body(get("/user/createAvatarFile").header("token", TOKEN), 200).getJSONObject("data");
        assertNotEquals(first.getString("fileId"), second.getString("fileId"));
        File saved = mongoTemplate.findById(first.getString("fileId"), File.class);
        assertEquals(USER_ID, saved.getUploaderId());
        assertEquals(FileType.AVATAR, saved.getFileType());
        assertEquals("avatar/" + USER_ID + "/" + saved.getId(), saved.getKey());
        assertEquals(saved.getKey(), first.getString("key"));
        assertNull(stored().getAvatarFileId());
        verifyNoInteractions(ossVideoService);
    }

    @Test
    void credentialsValidateTypeThenOwnershipBeforeCallingOss() throws Exception {
        file("fixture-wrong-type", "RAW_VIDEO", USER_ID);
        file("fixture-foreign", FileType.AVATAR, "fixture-other-owner");
        for (String id : new String[]{"fixture-missing", "fixture-wrong-type"}) {
            assertEquals(31, body(get("/user/getAvatarUploadCredentials").header("token", TOKEN)
                    .param("fileId", id), 200).getIntValue("code"));
        }
        assertEquals(33, body(get("/user/getAvatarUploadCredentials").header("token", TOKEN)
                .param("fileId", "fixture-foreign"), 200).getIntValue("code"));
        verifyNoInteractions(ossVideoService);
        File own = file("fixture-own", FileType.AVATAR, USER_ID);
        assertEquals(34, body(get("/user/getAvatarUploadCredentials").header("token", TOKEN)
                .param("fileId", own.getId()), 200).getIntValue("code"));
        JSONObject credentials = new JSONObject();
        credentials.put("fixtureField", "fixture-value");
        when(ossVideoService.generateUploadCredentials(own.getKey())).thenReturn(credentials);
        JSONObject result = body(get("/user/getAvatarUploadCredentials").header("token", TOKEN)
                .param("fileId", own.getId()), 200).getJSONObject("data");
        assertEquals(own.getId(), result.getString("fileId"));
        assertEquals("fixture-value", result.getString("fixtureField"));
        assertNull(stored().getAvatarFileId());
    }

    @Test
    void finishRequiresOwnedAvatarAndObjectThenUpdatesOnlyAvatarReference() throws Exception {
        file("fixture-foreign", FileType.AVATAR, "fixture-other-owner");
        assertEquals(33, body(get("/user/avatarUploadFinish").header("token", TOKEN)
                .param("fileId", "fixture-foreign"), 200).getIntValue("code"));
        verifyNoInteractions(ossVideoService);
        File own = file("fixture-own", FileType.AVATAR, USER_ID);
        assertEquals(31, body(get("/user/avatarUploadFinish").header("token", TOKEN)
                .param("fileId", own.getId()), 200).getIntValue("code"));
        assertNull(stored().getAvatarFileId());
        when(ossVideoService.doesObjectExist(own.getKey())).thenReturn(true);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertEquals(0, body(get("/user/avatarUploadFinish").header("token", TOKEN)
                    .param("fileId", own.getId()), 200).getIntValue("code"));
        }
        assertEquals(own.getId(), stored().getAvatarFileId());
        assertEquals(TOKEN, stored().getToken());
        assertEquals("https://example.invalid/legacy-avatar.png", stored().getAvatarUrl());
        assertTrue(stored().getUpdateTime().getTime() > 0);
        assertEquals(FileStatus.CREATED, mongoTemplate.findById(own.getId(), File.class).getFileStatus());
        verify(ossVideoService, times(3)).doesObjectExist(own.getKey());
    }

    @Test
    void avatarRoutesCurrentlyLackTheTokenInterceptorRegistration() throws Exception {
        JSONObject created = body(get("/user/createAvatarFile"), 200).getJSONObject("data");
        File anonymous = mongoTemplate.findById(created.getString("fileId"), File.class);
        assertNull(anonymous.getUploaderId());
        assertEquals("avatar/null/" + anonymous.getId(), anonymous.getKey());
        File own = file("fixture-own", FileType.AVATAR, USER_ID);
        for (String endpoint : new String[]{"getAvatarUploadCredentials", "avatarUploadFinish"}) {
            assertEquals(31, body(get("/user/" + endpoint).param("fileId", "fixture-missing"), 200)
                    .getIntValue("code"));
            JSONObject failed = body(get("/user/" + endpoint).param("fileId", own.getId()), 200);
            assertEquals(1, failed.getIntValue("code"));
            assertEquals("未知错误", failed.getString("message"));
        }
        assertNull(stored().getAvatarFileId());
        verifyNoInteractions(ossVideoService);
    }
}
