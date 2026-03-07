package com.github.makewheels.video2022.user;

import cn.hutool.json.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.system.context.RequestUtil;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.client.Client;
import com.github.makewheels.video2022.user.client.ClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class ClientServiceTest extends BaseIntegrationTest {

    @Autowired
    private ClientService clientService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @Test
    void requestClientId_shouldCreateClientAndReturnId() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("172.16.0.50");
        mockRequest.addHeader("User-Agent", "MobileApp/3.1");

        try (MockedStatic<RequestUtil> mocked = mockStatic(RequestUtil.class)) {
            mocked.when(RequestUtil::getRequest).thenReturn(mockRequest);

            Result<JSONObject> result = clientService.requestClientId();

            assertEquals(0, result.getCode());
            assertNotNull(result.getData());
            String clientId = result.getData().getStr("clientId");
            assertNotNull(clientId, "clientId should be present in response");
            assertFalse(clientId.isEmpty());

            // Verify client persisted in MongoDB
            Client saved = mongoTemplate.findById(clientId, Client.class);
            assertNotNull(saved, "Client should be saved in MongoDB");
            assertEquals("172.16.0.50", saved.getIp());
            assertEquals("MobileApp/3.1", saved.getUserAgent());
            assertNotNull(saved.getCreateTime());
        }
    }

    @Test
    void requestClientId_calledTwice_shouldCreateTwoDistinctClients() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("10.0.0.2");
        mockRequest.addHeader("User-Agent", "Desktop/1.0");

        try (MockedStatic<RequestUtil> mocked = mockStatic(RequestUtil.class)) {
            mocked.when(RequestUtil::getRequest).thenReturn(mockRequest);

            Result<JSONObject> first = clientService.requestClientId();
            Result<JSONObject> second = clientService.requestClientId();

            String firstId = first.getData().getStr("clientId");
            String secondId = second.getData().getStr("clientId");
            assertNotEquals(firstId, secondId, "Each call should create a distinct client");
        }
    }

    @Test
    void requestClientId_withNullUserAgent_shouldStillSucceed() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("192.168.0.1");

        try (MockedStatic<RequestUtil> mocked = mockStatic(RequestUtil.class)) {
            mocked.when(RequestUtil::getRequest).thenReturn(mockRequest);

            Result<JSONObject> result = clientService.requestClientId();

            assertEquals(0, result.getCode());
            String clientId = result.getData().getStr("clientId");
            assertNotNull(clientId);

            Client saved = mongoTemplate.findById(clientId, Client.class);
            assertNotNull(saved);
            assertEquals("192.168.0.1", saved.getIp());
            assertNull(saved.getUserAgent(), "User-Agent should be null when header is absent");
        }
    }
}
