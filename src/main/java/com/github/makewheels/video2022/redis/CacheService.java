package com.github.makewheels.video2022.redis;

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
     * 通用获取指定class对象方法
     */
    private <T> T getByClass(Class<T> clazz, String id) {
        //先从redis获取
        return mongoTemplate.findById(id, clazz);
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
