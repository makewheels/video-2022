package com.github.makewheels.video2022.user;

import cn.hutool.json.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.system.context.RequestUtil;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.session.Session;
import com.github.makewheels.video2022.user.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class SessionServiceTest extends BaseIntegrationTest {

    @Autowired
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @Test
    void requestSessionId_shouldCreateSessionAndReturnId() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("192.168.1.100");
        mockRequest.addHeader("User-Agent", "TestBrowser/1.0");

        try (MockedStatic<RequestUtil> mocked = mockStatic(RequestUtil.class)) {
            mocked.when(RequestUtil::getRequest).thenReturn(mockRequest);

            Result<JSONObject> result = sessionService.requestSessionId();

            assertEquals(0, result.getCode());
            assertNotNull(result.getData());
            String sessionId = result.getData().getStr("sessionId");
            assertNotNull(sessionId, "sessionId should be present in response");
            assertFalse(sessionId.isEmpty());

            // Verify session persisted in MongoDB
            Session saved = mongoTemplate.findById(sessionId, Session.class);
            assertNotNull(saved, "Session should be saved in MongoDB");
            assertEquals("192.168.1.100", saved.getIp());
            assertEquals("TestBrowser/1.0", saved.getUserAgent());
            assertNotNull(saved.getCreateTime());
        }
    }

    @Test
    void requestSessionId_calledTwice_shouldCreateTwoDistinctSessions() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("10.0.0.1");
        mockRequest.addHeader("User-Agent", "Agent/2.0");

        try (MockedStatic<RequestUtil> mocked = mockStatic(RequestUtil.class)) {
            mocked.when(RequestUtil::getRequest).thenReturn(mockRequest);

            Result<JSONObject> first = sessionService.requestSessionId();
            Result<JSONObject> second = sessionService.requestSessionId();

            String firstId = first.getData().getStr("sessionId");
            String secondId = second.getData().getStr("sessionId");
            assertNotEquals(firstId, secondId, "Each call should create a distinct session");
        }
    }

    @Test
    void requestSessionId_withNullUserAgent_shouldStillSucceed() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("127.0.0.1");
        // No User-Agent header set

        try (MockedStatic<RequestUtil> mocked = mockStatic(RequestUtil.class)) {
            mocked.when(RequestUtil::getRequest).thenReturn(mockRequest);

            Result<JSONObject> result = sessionService.requestSessionId();

            assertEquals(0, result.getCode());
            String sessionId = result.getData().getStr("sessionId");
            assertNotNull(sessionId);

            Session saved = mongoTemplate.findById(sessionId, Session.class);
            assertNotNull(saved);
            assertEquals("127.0.0.1", saved.getIp());
            assertNull(saved.getUserAgent(), "User-Agent should be null when header is absent");
        }
    }
}
