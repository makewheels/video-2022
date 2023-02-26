package com.github.makewheels.video2022.id;

import cn.hutool.core.util.RandomUtil;
import com.github.makewheels.video2022.redis.RedisKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.DecimalFormat;
import java.time.Duration;
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

    public synchronized String nextLongId() {
        //生成时间戳，以2022年1月1日作为起始时间
        long timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - 1640995200L;
        timestamp /= 1000;

        //生成序列号
        Long redisIncreaseId = stringRedisTemplate.opsForValue().increment(RedisKey.increaseLongId(timestamp));
        stringRedisTemplate.expire(RedisKey.increaseShortId(), Duration.ofSeconds(2));

        String increaseId = new DecimalFormat(StringUtils.repeat("0", 7))
                .format(redisIncreaseId);

        //生成随机数结尾
        String random = RandomUtil.randomNumbers(5);

        //拼接返回
        long decimal = Long.parseLong(timestamp + increaseId + random);
        String result = Long.toString(decimal, Character.MAX_RADIX).toUpperCase();

        log.info("生成id：format = {}, decimal = {}, result = {}",
                timestamp + "-" + increaseId + "-" + random, decimal, result);

        return result;
    }

    /**
     * 生成shortId
     */
    public synchronized String nextShortId() {
        //生成时间戳
        long currentSeconds = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        // shortId以2022年1月1日零点作为起始时间偏移量
        long timestamp = currentSeconds - 1640995200L;
        timestamp = timestamp / 60 / 60 / 24;

        //生成序列号
        String redisKey = RedisKey.increaseShortId();
        Long redisIncreaseId = stringRedisTemplate.opsForValue().increment(redisKey);
        stringRedisTemplate.expire(RedisKey.increaseShortId(), Duration.ofDays(2));

        if (redisIncreaseId == null) {
            redisIncreaseId = 0L;
        }
        String increaseId = new DecimalFormat("00").format(redisIncreaseId);

        //生成随机数结尾
        String random = RandomUtil.randomNumbers(3);

        //拼接返回
        long decimal = Long.parseLong(timestamp + increaseId + random);
        String result = Long.toString(decimal, Character.MAX_RADIX).toUpperCase();

        String format = timestamp + "-" + increaseId + "-" + random;
        log.info("生成id：format = {}, result = {}", format, result);

        return result;
    }

    public String getVideoId() {
        return "VID" + nextLongId();
    }
}
