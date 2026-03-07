package com.github.makewheels.video2022.etc.redis;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class RedisServiceTest extends BaseIntegrationTest {

    private static final String TEST_PREFIX = "video-2022:test:";

    @Autowired
    private RedisService redisService;

    @BeforeEach
    void setUp() {
        cleanRedisKeys(TEST_PREFIX + "*");
    }

    @AfterEach
    void tearDown() {
        cleanRedisKeys(TEST_PREFIX + "*");
    }

    // ---- set & get ----

    @Test
    void set_andGet_shouldStoreAndRetrieveValue() {
        String key = TEST_PREFIX + "string-key";
        boolean ok = redisService.set(key, "hello", RedisTime.ONE_MINUTE);

        assertTrue(ok, "set should return true on success");

        Object value = redisService.get(key);
        assertEquals("hello", value);
    }

    @Test
    void get_withNonExistentKey_shouldReturnNull() {
        Object value = redisService.get(TEST_PREFIX + "non-existent");
        assertNull(value);
    }

    @Test
    void get_withNullKey_shouldReturnNull() {
        Object value = redisService.get(null);
        assertNull(value);
    }

    // ---- getForJSONObject ----

    @Test
    void getForJSONObject_shouldParseStoredJson() {
        String key = TEST_PREFIX + "json-key";
        JSONObject obj = new JSONObject();
        obj.put("name", "test");
        obj.put("count", 42);
        redisService.set(key, obj.toJSONString(), RedisTime.ONE_MINUTE);

        JSONObject result = redisService.getForJSONObject(key);

        assertNotNull(result);
        assertEquals("test", result.getString("name"));
        assertEquals(42, result.getIntValue("count"));
    }

    @Test
    void getForJSONObject_withNonExistentKey_shouldReturnNull() {
        JSONObject result = redisService.getForJSONObject(TEST_PREFIX + "missing");
        assertNull(result);
    }

    // ---- del ----

    @Test
    void del_singleKey_shouldRemoveValue() {
        String key = TEST_PREFIX + "del-single";
        redisService.set(key, "toDelete", RedisTime.ONE_MINUTE);
        assertNotNull(redisService.get(key));

        redisService.del(key);

        assertNull(redisService.get(key));
    }

    @Test
    void del_multipleKeys_shouldRemoveAllValues() {
        String key1 = TEST_PREFIX + "del-multi-1";
        String key2 = TEST_PREFIX + "del-multi-2";
        redisService.set(key1, "v1", RedisTime.ONE_MINUTE);
        redisService.set(key2, "v2", RedisTime.ONE_MINUTE);

        redisService.del(key1, key2);

        assertNull(redisService.get(key1));
        assertNull(redisService.get(key2));
    }

    @Test
    void del_withNullKey_shouldNotThrow() {
        assertDoesNotThrow(() -> redisService.del((String[]) null));
    }

    // ---- TTL ----

    @Test
    void set_withShortTtl_shouldExpire() throws InterruptedException {
        String key = TEST_PREFIX + "ttl-key";
        redisService.set(key, "ephemeral", 1); // 1 second TTL

        assertNotNull(redisService.get(key), "Key should exist immediately after set");

        Thread.sleep(1500);

        assertNull(redisService.get(key), "Key should have expired after TTL");
    }
}
