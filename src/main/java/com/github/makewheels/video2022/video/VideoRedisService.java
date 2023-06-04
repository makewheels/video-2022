package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.redis.RedisKey;
import com.github.makewheels.video2022.redis.RedisService;
import com.github.makewheels.video2022.redis.RedisTime;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfoVO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class VideoRedisService {
    @Resource
    private RedisService redisService;

    public WatchInfoVO getWatchInfo(String watchId) {
        String json = (String) redisService.get(RedisKey.watchInfo(watchId));
        return JSON.parseObject(json, WatchInfoVO.class);
    }

    public void setWatchInfo(String watchId, WatchInfoVO watchInfoVO) {
        redisService.set(RedisKey.watchInfo(watchId),
                JSON.toJSONString(watchInfoVO), RedisTime.SIX_HOURS);
    }
}
