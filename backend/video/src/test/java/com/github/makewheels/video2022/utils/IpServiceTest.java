package com.github.makewheels.video2022.utils;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.redis.RedisKey;
import com.github.makewheels.video2022.etc.redis.RedisService;
import com.github.makewheels.video2022.etc.redis.RedisTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpServiceTest {

    @Mock
    private RedisService redisService;

    @InjectMocks
    private IpService ipService;

    private static final String TEST_IP = "45.78.33.111";
    private static final String REDIS_KEY = RedisKey.ip(TEST_IP);

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
    void getIpWithRedis_cacheHit_shouldReturnCachedValue() {
        JSONObject cached = new JSONObject();
        cached.put("ip", TEST_IP);
        cached.put("nation", "中国");
        when(redisService.getForJSONObject(REDIS_KEY)).thenReturn(cached);

        JSONObject result = ipService.getIpWithRedis(TEST_IP);

        assertNotNull(result);
        assertEquals(TEST_IP, result.getString("ip"));
        assertEquals("中国", result.getString("nation"));
        verify(redisService).getForJSONObject(REDIS_KEY);
        // Should not call set — cache already exists
        verify(redisService, never()).set(anyString(), any(), anyLong());
    }

    @Test
    void getIpWithRedis_cacheMiss_shouldCallApiAndCache() {
        setupAppCode();
        when(redisService.getForJSONObject(REDIS_KEY)).thenReturn(null);
        when(redisService.set(anyString(), any(), anyLong())).thenReturn(true);

        JSONObject apiResponse = buildApiResponse();
        HttpRequest mockHttpRequest = mock(HttpRequest.class);
        HttpResponse mockHttpResponse = mock(HttpResponse.class);
        when(mockHttpRequest.header(anyString(), anyString())).thenReturn(mockHttpRequest);
        when(mockHttpRequest.execute()).thenReturn(mockHttpResponse);
        when(mockHttpResponse.body()).thenReturn(apiResponse.toJSONString());

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            httpUtilMock.when(() -> HttpUtil.createGet(anyString())).thenReturn(mockHttpRequest);

            JSONObject result = ipService.getIpWithRedis(TEST_IP);

            assertNotNull(result);
            assertEquals(TEST_IP, result.getString("ip"));
            assertEquals("中国", result.getString("nation"));
            assertEquals("香港特别行政区", result.getString("province"));

            // Verify cached to Redis
            verify(redisService).set(eq(REDIS_KEY), anyString(), eq(RedisTime.SIX_HOURS));
        }
    }

    @Test
    void getIpWithRedis_ipv6WithColons_shouldReplaceColonsInRedisKey() {
        String ipv6 = "2001:db8::1";
        String expectedKey = RedisKey.ip("2001_db8__1");

        JSONObject cached = new JSONObject();
        cached.put("ip", ipv6);
        when(redisService.getForJSONObject(expectedKey)).thenReturn(cached);

        JSONObject result = ipService.getIpWithRedis(ipv6);

        assertNotNull(result);
        verify(redisService).getForJSONObject(expectedKey);
    }

    @Test
    void getIpWithRedis_cacheMiss_shouldFlattenResultField() {
        setupAppCode();
        when(redisService.getForJSONObject(REDIS_KEY)).thenReturn(null);
        when(redisService.set(anyString(), any(), anyLong())).thenReturn(true);

        JSONObject apiResponse = buildApiResponse();
        HttpRequest mockHttpRequest = mock(HttpRequest.class);
        HttpResponse mockHttpResponse = mock(HttpResponse.class);
        when(mockHttpRequest.header(anyString(), anyString())).thenReturn(mockHttpRequest);
        when(mockHttpRequest.execute()).thenReturn(mockHttpResponse);
        when(mockHttpResponse.body()).thenReturn(apiResponse.toJSONString());

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            httpUtilMock.when(() -> HttpUtil.createGet(anyString())).thenReturn(mockHttpRequest);

            JSONObject result = ipService.getIpWithRedis(TEST_IP);

            // "result" nested object should be flattened into the top level
            assertNull(result.getJSONObject("result"), "Nested 'result' should be removed");
            assertEquals("CN", result.getString("en_short"));
            assertEquals(22.27628, result.getDoubleValue("lat"), 0.001);
        }
    }
}
