package com.github.makewheels.video2022.utils;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Real old IpService and isolated Mongo; static HTTP is entirely synthetic. */
class IpCacheMappingContractTest extends BaseIntegrationTest {
    private IpService actual;
    private static final String IP = "2001:db8::1";
    private static final String KEY = "2001_db8__1";

    @BeforeEach
    void setUp() {
        cleanDatabase();
        actual = new IpService();
        ReflectionTestUtils.setField(actual, "mongoTemplate", mongoTemplate);
        ReflectionTestUtils.setField(actual, "appCode", "fixture-not-a-real-appcode");
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private HttpRequest request(String body) {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        when(request.header("Authorization", "APPCODE fixture-not-a-real-appcode")).thenReturn(request);
        when(request.execute()).thenReturn(response);
        when(response.body()).thenReturn(body);
        return request;
    }

    @Test
    void existingOldIpv6CacheIsUsedWithoutRefreshOrHttp() {
        mongoTemplate.getCollection("ip_cache").insertOne(new Document("ip", KEY)
                .append("locationJson", "{\"province\":\"fixture-cache\"}").append("createdAt", new Date(0)));
        try (var http = mockStatic(HttpUtil.class)) {
            assertEquals("fixture-cache", actual.getIpInfo(IP).getString("province"));
            http.verifyNoInteractions();
        }
        assertEquals(new Date(0), mongoTemplate.getCollection("ip_cache").find().first().getDate("createdAt"));
    }

    @Test
    void missFlattensNestedOverridesAndCacheJsonOmitsMapNullButKeepsDecimalAndListNull() {
        HttpRequest request = request("{\"code\":100,\"ip\":\"outer\",\"result\":{\"ip\":\"nested\","
                + "\"nullable\":null,\"decimal\":1.2500,\"list\":[null,1.50]}}");
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.createGet("https://ips.market.alicloudapi.com/iplocaltion?ip=" + IP))
                    .thenReturn(request);
            JSONObject first = actual.getIpInfo(IP);
            assertEquals("nested", first.getString("ip"));
            assertEquals(100, first.getIntValue("code"));
            assertFalse(first.containsKey("result"));
            assertTrue(first.containsKey("nullable"));
            Document cache = mongoTemplate.getCollection("ip_cache").find().first();
            assertEquals(KEY, cache.getString("ip"));
            assertNotNull(cache.getDate("createdAt"));
            assertInstanceOf(org.bson.types.ObjectId.class, cache.get("_id"));
            assertFalse(cache.containsKey("_class"));
            assertTrue(cache.getString("locationJson").contains("1.2500"));
            JSONObject second = actual.getIpInfo(IP);
            assertFalse(second.containsKey("nullable"));
            assertEquals(new java.math.BigDecimal("1.2500"), second.getBigDecimal("decimal"));
            assertNull(second.getJSONArray("list").get(0));
            verify(request).execute();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null"})
    void presentEmptyOrNullJsonIsReturnedAsNullWithoutHttp(String json) {
        mongoTemplate.getCollection("ip_cache").insertOne(new Document("ip", KEY).append("locationJson", json));
        try (var http = mockStatic(HttpUtil.class)) {
            assertNull(actual.getIpInfo(IP));
            http.verifyNoInteractions();
        }
    }

    @Test
    void missingResultFailsBeforeCacheInsert() {
        HttpRequest request = request("{\"code\":400,\"message\":\"fixture-failure\"}");
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.createGet(anyString())).thenReturn(request);
            assertThrows(NullPointerException.class, () -> actual.getIpInfo(IP));
            assertEquals(0, mongoTemplate.getCollection("ip_cache").countDocuments());
            verify(request).execute();
        }
    }

    @Test
    void actualEntityIndexesHaveUniqueIpAndSixHourTtl() {
        var resolver = new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext());
        for (var index : resolver.resolveIndexFor(IpCache.class)) mongoTemplate.indexOps(IpCache.class).createIndex(index);
        boolean unique = false;
        boolean ttl = false;
        for (Document index : mongoTemplate.getCollection("ip_cache").listIndexes()) {
            var keys = index.get("key", Document.class);
            if (keys.containsKey("ip")) unique = Boolean.TRUE.equals(index.getBoolean("unique"));
            if (keys.containsKey("createdAt")) {
                assertEquals(21600L, ((Number) index.get("expireAfterSeconds")).longValue());
                ttl = true;
            }
        }
        assertTrue(unique);
        assertTrue(ttl);
    }
}
