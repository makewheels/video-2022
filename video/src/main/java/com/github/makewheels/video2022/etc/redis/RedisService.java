package com.github.makewheels.video2022.etc.redis;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.endpoint.LocalConfigGlobalEndpointResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Redis工具类
 */
@Service
@Deprecated
@Slf4j
public class RedisService {
    @Resource
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 删除缓存
     *
     * @param key 可以传一个值 或多个
     */
    @SuppressWarnings("unchecked")
    public void del(String... key) {
        if (key != null && key.length > 0) {
            if (key.length == 1) {
                redisTemplate.delete(key[0]);
            } else {
                redisTemplate.delete(Arrays.asList(key));
            }
        }
    }

    /**
     * 普通缓存获取
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

    public JSONObject getForJSONObject(String key) {
        String json = (String) redisTemplate.opsForValue().get(key);
        return JSON.parseObject(json);
    }

    /**
     * 普通缓存放入并设置时间
     */
    public boolean set(String key, Object value, long time) {
        try {
            redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            return false;
        }
    }

}

