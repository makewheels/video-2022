package com.github.makewheels.video2022.redis;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.transcode.Transcode;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.Video;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 通用缓存类，为解决循环依赖而生
 * 先从redis获取，再从mongo获取
 */
@Service
public class CacheService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private RedisService redisService;

    /**
     * 获取redis的key
     */
    private <T> String getRedisKey(Class<T> clazz, String id) {
        if (clazz.equals(Video.class)) {
            return RedisKey.videoCache(id);
        } else if (clazz.equals(Transcode.class)) {
            return RedisKey.userCache(id);
        } else if (clazz.equals(User.class)) {
            return RedisKey.transcodeCache(id);
        }
        return null;
    }

    /**
     * 通用获取指定class对象方法
     */
    private <T> T getByClass(Class<T> clazz, String id) {
        //先从redis获取key
        String redisKey = getRedisKey(clazz, id);
        String json = redisService.getForString(redisKey);
        T instance = JSON.parseObject(json, clazz);

        //如果redis没获取到，从mongo查出来，缓存到redis
        if (instance == null) {
            instance = mongoTemplate.findById(id, clazz);
            if (instance != null) {
                redisService.set(redisKey, JSON.toJSONString(instance), RedisTime.SIX_HOURS);
            }
        }
        return instance;
    }

    public Video getVideo(String id) {
        return getByClass(Video.class, id);
    }

    public User getUser(String id) {
        return getByClass(User.class, id);
    }

    public Transcode getTranscode(String id) {
        return getByClass(Transcode.class, id);
    }

}