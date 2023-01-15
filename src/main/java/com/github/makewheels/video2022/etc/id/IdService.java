package com.github.makewheels.video2022.etc.id;

import cn.hutool.core.util.RandomUtil;
import com.github.makewheels.video2022.redis.RedisKey;
import com.github.makewheels.video2022.redis.RedisService;
import com.github.makewheels.video2022.redis.RedisTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 自定义短id
 */
@Service
@Slf4j
public class IdService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisService redisService;

    //以2022年1月1日零点作为起始时间偏移量
    private final static long START_TIME_IN_SECONDS = 1640995200L;

    /**
     * 生成id
     */
    public String nextId() {
        //生成时间戳
        long currentSeconds = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = currentSeconds - START_TIME_IN_SECONDS;
        timestamp = timestamp / 60 / 60 / 24;

        //生成序列号
        String redisKey = RedisKey.increaseId();
        Long redisIncreaseId = stringRedisTemplate.opsForValue().increment(redisKey);
        redisService.expire(redisKey, RedisTime.TWO_DAYS);

        if (redisIncreaseId == null) {
            redisIncreaseId = 0L;
        }
        String increaseId = new DecimalFormat("00").format(redisIncreaseId);

        //生成随机数结尾
        String random = RandomUtil.randomNumbers(3);

        //拼接返回
        long decimal = Long.parseLong(timestamp + increaseId + random);
        String result = Long.toString(decimal, Character.MAX_RADIX).toUpperCase();

        log.info("生成id：format = {}, decimal = {}, result = {}",
                timestamp + "-" + increaseId + "-" + random, decimal, result);

        return result;
    }

}
