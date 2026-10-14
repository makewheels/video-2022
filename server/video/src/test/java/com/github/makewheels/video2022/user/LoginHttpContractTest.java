package com.github.makewheels.video2022.user;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.finance.wallet.Wallet;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.user.bean.VerificationCode;
import com.github.makewheels.video2022.user.client.Client;
import com.github.makewheels.video2022.user.session.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Existing HTTP behavior on fixed, disposable identities; no live login is replayed. */
class LoginHttpContractTest extends BaseIntegrationTest {
    private static final String PHONE = "13800000000";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
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

    private VerificationCode code() {
        VerificationCode code = new VerificationCode();
        code.setPhone(PHONE);
        code.setCode("1234");
        code.setCreatedAt(new Date());
        mongoTemplate.save(code);
        return code;
    }

    private MockHttpServletRequestBuilder submit(String code) {
        return get("/user/submitVerificationCode").param("phone", PHONE).param("code", code);
    }

    private User user() {
        User user = new User();
        user.setId("fixture-login-user");
        user.setPhone(PHONE);
        user.setToken("fixture-old-token");
        mongoTemplate.save(user);
        return user;
    }

    @Test
    void requestingCodeReusesExistingRecordAndCreationTime() throws Exception {
        VerificationCode original = code();
        JSONObject response = body(get("/user/requestVerificationCode").param("phone", PHONE), 200);
        assertEquals(0, response.getIntValue("code"));
        assertEquals("success", response.getString("message"));
        assertNull(response.get("data"));
        VerificationCode stored = mongoTemplate.findById(original.getId(), VerificationCode.class);
        assertEquals(original.getCreatedAt(), stored.getCreatedAt());
        assertEquals(original.getCode(), stored.getCode());
        assertEquals(1, mongoTemplate.count(new Query(), VerificationCode.class));
    }

    @Test
    void wrongAndAbsentCodesUseExistingErrorsWithoutConsumingWrongCode() throws Exception {
        JSONObject absent = body(submit("1234"), 200);
        assertEquals(12, absent.getIntValue("code"));
        assertEquals("验证码已过期", absent.getString("message"));
        VerificationCode original = code();
        JSONObject wrong = body(submit("9999"), 200);
        assertEquals(11, wrong.getIntValue("code"));
        assertEquals("验证码错误", wrong.getString("message"));
        assertNotNull(mongoTemplate.findById(original.getId(), VerificationCode.class));
        assertEquals(0, mongoTemplate.count(new Query(), User.class));
        assertEquals(0, mongoTemplate.count(new Query(), Wallet.class));
    }

    @Test
    void successfulLoginRotatesTokenAndPreservesExistingWallet() throws Exception {
        User existing = user();
        Wallet wallet = new Wallet();
        wallet.setUserId(existing.getId());
        wallet.setBalance(new BigDecimal("12.34"));
        mongoTemplate.save(wallet);
        code();
        JSONObject response = body(submit("1234"), 200);
        assertEquals(0, response.getIntValue("code"));
        JSONObject loggedIn = response.getJSONObject("data");
        String token = loggedIn.getString("token");
        assertNotNull(token);
        assertNotEquals(existing.getToken(), token);
        assertEquals(existing.getId(), loggedIn.getString("id"));
        assertEquals(0, mongoTemplate.count(new Query(), VerificationCode.class));
        assertEquals(1, mongoTemplate.count(new Query(), Wallet.class));
        assertEquals(0, wallet.getBalance().compareTo(mongoTemplate.findById(wallet.getId(), Wallet.class).getBalance()));
        assertEquals(13, body(get("/user/getUserByToken").param("token", existing.getToken()), 200).getIntValue("code"));
        assertEquals(0, body(get("/user/getUserByToken").param("token", token), 200).getIntValue("code"));
    }

    @Test
    void nonProductionBypassStillNeedsRecordAndCreatesWallet() throws Exception {
        assertEquals(12, body(submit("111"), 200).getIntValue("code"));
        code();
        JSONObject response = body(submit("111"), 200);
        assertEquals(0, response.getIntValue("code"));
        String userId = response.getJSONObject("data").getString("id");
        assertNotNull(userId);
        Wallet wallet = mongoTemplate.findOne(Query.query(Criteria.where("userId").is(userId)), Wallet.class);
        assertNotNull(wallet);
        assertEquals(0, wallet.getBalance().compareTo(BigDecimal.ZERO));
        assertEquals(0, mongoTemplate.count(new Query(), VerificationCode.class));
    }

    @Test
    void publicUserRedactionDoesNotRevokeTokenAndHeadersTakePrecedence() throws Exception {
        User existing = user();
        JSONObject publicUser = body(get("/user/getUserById").param("userId", existing.getId()), 200);
        assertTrue(publicUser.getJSONObject("data").containsKey("token"));
        assertNull(publicUser.getJSONObject("data").get("token"));
        assertEquals(existing.getToken(), mongoTemplate.findById(existing.getId(), User.class).getToken());
        JSONObject denied = body(get("/user/getMyProfile"), 401);
        assertEquals(401, denied.getIntValue("code"));
        assertEquals("请先登录", denied.getString("message"));
        assertFalse(denied.containsKey("data"));
        assertEquals(401, body(get("/user/getMyProfile").header("token", "wrong-fixture")
                .param("token", existing.getToken()), 401).getIntValue("code"));
        JSONObject profile = body(get("/user/getMyProfile").header("token", "")
                .param("token", existing.getToken()), 200);
        assertEquals(0, profile.getIntValue("code"));
        assertNull(profile.getJSONObject("data").get("token"));
    }

    @Test
    void clientAndSessionAllocateEachRequestAndPersistRequestMetadata() throws Exception {
        String first = body(get("/client/requestClientId").header("User-Agent", "fixture-agent"), 200)
                .getJSONObject("data").getString("clientId");
        String second = body(get("/client/requestClientId"), 200).getJSONObject("data").getString("clientId");
        assertNotNull(first);
        assertNotEquals(first, second);
        Client client = mongoTemplate.findById(first, Client.class);
        assertEquals("fixture-agent", client.getUserAgent());
        assertEquals("127.0.0.1", client.getIp());
        String sessionId = body(get("/session/requestSessionId").header("User-Agent", "fixture-session"), 200)
                .getJSONObject("data").getString("sessionId");
        Session session = mongoTemplate.findById(sessionId, Session.class);
        assertEquals("fixture-session", session.getUserAgent());
        assertEquals("127.0.0.1", session.getIp());
        String next = body(get("/session/requestSessionId"), 200).getJSONObject("data").getString("sessionId");
        assertNotEquals(sessionId, next);
        assertEquals(2, mongoTemplate.count(new Query(), Client.class));
        assertEquals(2, mongoTemplate.count(new Query(), Session.class));
    }
}
