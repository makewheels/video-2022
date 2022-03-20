package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.redis.RedisKey;
import com.github.makewheels.video2022.redis.RedisService;
import com.github.makewheels.video2022.redis.RedisTime;
import com.github.makewheels.video2022.video.watch.WatchInfo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class VideoRedisService {
    @Resource
    private RedisService redisService;

    public WatchInfo getWatchInfo(String watchId) {
        String json = (String) redisService.get(RedisKey.watchInfo(watchId));
        return JSON.parseObject(json, WatchInfo.class);
    }

    public void setWatchInfo(WatchInfo watchInfo) {
        redisService.set(RedisKey.watchInfo(watchInfo.getWatchId()),
                JSON.toJSONString(watchInfo), RedisTime.SIX_HOURS);
    }
}
