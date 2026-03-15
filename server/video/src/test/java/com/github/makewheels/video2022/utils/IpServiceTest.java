package com.github.makewheels.video2022.utils;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private IpService ipService;

    private static final String TEST_IP = "45.78.33.111";

    private JSONObject buildApiResponse() {
        JSONObject result = new JSONObject();
        result.put("en_short", "CN");
        result.put("en_name", "China");
        result.put("nation", "中国");
        result.put("province", "香港特别行政区");
        result.put("city", "");
        result.put("district", "");
        result.put("adcode", "810000");
        result.put("lat", 22.27628);
        result.put("lng", 114.16383);

        JSONObject response = new JSONObject();
        response.put("code", 100);
        response.put("message", "success");
        response.put("ip", TEST_IP);
        response.put("result", result);
        return response;
    }

    private void setupAppCode() {
        ReflectionTestUtils.setField(ipService, "appCode", "test-app-code");
    }

    @Test
    void getIpInfo_cacheHit_shouldReturnCachedValue() {
        JSONObject cached = new JSONObject();
        cached.put("ip", TEST_IP);
        cached.put("nation", "中国");

        IpCache ipCache = new IpCache();
        ipCache.setIp(TEST_IP);
        ipCache.setLocationJson(cached.toJSONString());
        when(mongoTemplate.findOne(any(Query.class), eq(IpCache.class))).thenReturn(ipCache);

        JSONObject result = ipService.getIpInfo(TEST_IP);

        assertNotNull(result);
        assertEquals(TEST_IP, result.getString("ip"));
        assertEquals("中国", result.getString("nation"));
        verify(mongoTemplate).findOne(any(Query.class), eq(IpCache.class));
        // Should not call save — cache already exists
        verify(mongoTemplate, never()).save(any(IpCache.class));
    }

    @Test
    void getIpInfo_cacheMiss_shouldCallApiAndCache() {
        setupAppCode();
        when(mongoTemplate.findOne(any(Query.class), eq(IpCache.class))).thenReturn(null);

        JSONObject apiResponse = buildApiResponse();
        HttpRequest mockHttpRequest = mock(HttpRequest.class);
        HttpResponse mockHttpResponse = mock(HttpResponse.class);
        when(mockHttpRequest.header(anyString(), anyString())).thenReturn(mockHttpRequest);
        when(mockHttpRequest.execute()).thenReturn(mockHttpResponse);
        when(mockHttpResponse.body()).thenReturn(apiResponse.toJSONString());

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            httpUtilMock.when(() -> HttpUtil.createGet(anyString())).thenReturn(mockHttpRequest);

            JSONObject result = ipService.getIpInfo(TEST_IP);

            assertNotNull(result);
            assertEquals(TEST_IP, result.getString("ip"));
            assertEquals("中国", result.getString("nation"));
            assertEquals("香港特别行政区", result.getString("province"));

            // Verify cached to MongoDB
            verify(mongoTemplate).save(any(IpCache.class));
        }
    }

    @Test
    void getIpInfo_ipv6WithColons_shouldReplaceColonsInCacheKey() {
        String ipv6 = "2001:db8::1";
        String expectedCacheKey = "2001_db8__1";

        JSONObject cached = new JSONObject();
        cached.put("ip", ipv6);

        IpCache ipCache = new IpCache();
        ipCache.setIp(expectedCacheKey);
        ipCache.setLocationJson(cached.toJSONString());
        when(mongoTemplate.findOne(any(Query.class), eq(IpCache.class))).thenReturn(ipCache);

        JSONObject result = ipService.getIpInfo(ipv6);

        assertNotNull(result);
        verify(mongoTemplate).findOne(any(Query.class), eq(IpCache.class));
    }

    @Test
    void getIpInfo_cacheMiss_shouldFlattenResultField() {
        setupAppCode();
        when(mongoTemplate.findOne(any(Query.class), eq(IpCache.class))).thenReturn(null);

        JSONObject apiResponse = buildApiResponse();
        HttpRequest mockHttpRequest = mock(HttpRequest.class);
        HttpResponse mockHttpResponse = mock(HttpResponse.class);
        when(mockHttpRequest.header(anyString(), anyString())).thenReturn(mockHttpRequest);
        when(mockHttpRequest.execute()).thenReturn(mockHttpResponse);
        when(mockHttpResponse.body()).thenReturn(apiResponse.toJSONString());

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            httpUtilMock.when(() -> HttpUtil.createGet(anyString())).thenReturn(mockHttpRequest);

            JSONObject result = ipService.getIpInfo(TEST_IP);

            // "result" nested object should be flattened into the top level
            assertNull(result.getJSONObject("result"), "Nested 'result' should be removed");
            assertEquals("CN", result.getString("en_short"));
            assertEquals(22.27628, result.getDoubleValue("lat"), 0.001);
        }
    }
}
