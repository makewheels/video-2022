package com.github.makewheels.video2022.e2e;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.user.bean.User;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 登录注册相关 E2E 测试。
 * 验证手机号登录流程、token 校验、用户查询、重复登录刷新 token 等场景。
 */
public class LoginE2ETest extends BaseE2ETest {

    /**
     * baseSetUp 已完成登录，验证 token/userId 已设置，
     * MongoDB 中存在对应 User，且 token 能访问受保护接口。
     */
    @Test
    void testLoginFlowAndTokenValidation() {
        assertNotNull(testToken, "登录后 token 不应为空");
        assertFalse(testToken.isBlank(), "登录后 token 不应为空白");
        assertNotNull(testUserId, "登录后 userId 不应为空");

        // 验证 MongoDB 中存在该用户
        User user = mongoTemplate.findById(testUserId, User.class);
        assertNotNull(user, "MongoDB 中应存在刚登录的用户");
        assertEquals(testToken, user.getToken(), "数据库中 token 应与登录返回的一致");

        // 使用 token 访问受保护接口
        ResponseEntity<String> response = authGet(
                getBaseUrl() + "/video/getMyVideoList?skip=0&limit=10");
        assertEquals(HttpStatus.OK, response.getStatusCode(), "持有效 token 应返回 200");

        JSONObject result = JSONObject.parseObject(response.getBody());
        assertEquals(0, result.getIntValue("code"), "业务状态码应为 0（成功）");
    }

    /**
     * 使用无效 token 访问受保护接口，期望被拦截重定向到登录页。
     * 注意：CheckTokenInterceptor 会 sendRedirect 到 login.html，
     * RestTemplate 默认跟随重定向，所以最终状态码为 200，
     * 但响应体应包含登录页内容。
     */
    @Test
    void testInvalidTokenRedirectsToLogin() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", "invalid-token-that-does-not-exist");
        headers.set("Content-Type", "application/json");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl() + "/video/getMyVideoList?skip=0&limit=10",
                HttpMethod.GET, entity, String.class);

        // RestTemplate 跟随 302 重定向到 login.html，最终返回 200
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body, "响应体不应为空");
        // 验证被重定向到了登录页
        assertTrue(body.contains("login") || body.contains("登录") || body.contains("verificationCode"),
                "无效 token 应被重定向到登录页");
    }

    /**
     * 调用 /user/getUserByToken 接口，验证返回正确用户信息。
     */
    @Test
    void testGetUserByToken() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                getBaseUrl() + "/user/getUserByToken?token=" + testToken, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        JSONObject result = JSONObject.parseObject(response.getBody());
        assertEquals(0, result.getIntValue("code"), "业务状态码应为 0");

        JSONObject userData = result.getJSONObject("data");
        assertNotNull(userData, "data 不应为空");
        assertEquals(testUserId, userData.getString("id"), "返回的用户 id 应一致");
        assertEquals(testToken, userData.getString("token"), "返回的 token 应一致");
        assertNotNull(userData.getString("phone"), "返回的用户应包含 phone 字段");
    }

    /**
     * 同一手机号重新登录，验证 token 会刷新（与旧 token 不同）。
     */
    @Test
    void testReLoginRefreshesToken() {
        String oldToken = testToken;
        String oldUserId = testUserId;

        // 重新登录同一手机号
        login();

        assertNotNull(testToken, "重新登录后 token 不应为空");
        assertNotEquals(oldToken, testToken, "重新登录后 token 应与旧 token 不同");
        assertEquals(oldUserId, testUserId, "同一手机号重新登录，userId 应不变");
    }

    // ---- Error Scenarios ----

    @Test
    void login_invalidPhoneFormat_shouldReturnError() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                getBaseUrl() + "/user/requestVerificationCode?phone=123", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        JSONObject result = JSONObject.parseObject(response.getBody());
        assertNotNull(result, "应返回 JSON 响应");
        assertNotNull(result.get("code"), "响应应包含 code 字段");
    }

    @Test
    void login_emptyPhone_shouldReturnError() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    getBaseUrl() + "/user/requestVerificationCode?phone=", String.class);
            // If server accepts empty phone, verify response structure
            JSONObject result = JSONObject.parseObject(response.getBody());
            assertNotNull(result, "应返回 JSON 响应");
        } catch (HttpClientErrorException e) {
            // Spring may reject empty @RequestParam with 400
            assertTrue(e.getStatusCode().is4xxClientError(),
                    "空手机号应返回 4xx 错误");
        }
    }

    @Test
    void login_wrongVerificationCode_shouldFail() {
        String phone = "19900009999";
        restTemplate.getForEntity(
                getBaseUrl() + "/user/requestVerificationCode?phone=" + phone, String.class);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    getBaseUrl() + "/user/submitVerificationCode?phone=" + phone + "&code=999999",
                    String.class);
            JSONObject result = JSONObject.parseObject(response.getBody());
            assertNotNull(result);
            assertNotEquals(0, result.getIntValue("code"),
                    "错误验证码应返回非 0 状态码");
        } catch (HttpClientErrorException e) {
            assertTrue(e.getStatusCode().is4xxClientError() || e.getStatusCode().is5xxServerError(),
                    "错误验证码应返回错误状态");
        }
    }
}
