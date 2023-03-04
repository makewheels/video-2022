package com.github.makewheels.video2022.utils;

import cn.hutool.core.util.RandomUtil;
import com.github.makewheels.video2022.redis.RedisKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${spring.profiles.active}")
    private String environment;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public synchronized String nextId(
            Duration duration, int serialNumberLength, int randomLength) {
        // 时间戳，当前时间
        long currentTime = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
        // 起始时间
        long startTime = LocalDateTime.of(2023, 1, 1, 0, 0, 0)
                .toInstant(ZoneOffset.UTC).toEpochMilli();
        long timeUnit = (currentTime - startTime) / duration.toMillis();

        // 生成序列号
        String redisKey = RedisKey.increaseLongId(timeUnit);
        Long redisIncreaseId = stringRedisTemplate.opsForValue().increment(redisKey);
        stringRedisTemplate.expire(redisKey, duration.plus(duration));

        String serialNumber = new DecimalFormat(StringUtils.repeat("0", serialNumberLength))
                .format(redisIncreaseId);

        // 生成随机数
        String random = RandomUtil.randomNumbers(randomLength);
        log.info("生成id: " + timeUnit + "-" + serialNumber + "-" + random);

        // 拼接返回
        return timeUnit + serialNumber + random;
    }

    public synchronized String nextLongId() {
        return nextId(Duration.ofSeconds(4), 5, 3);
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

    public String getEnvironmentPrefix() {
        switch (environment) {
            case Environment.PRODUCTION:
                return "P";
            case Environment.PREVIEW:
                return "V";
            case Environment.DEVELOPMENT:
                return "D";
        }
        return "";
    }

    public String getVideoId() {
        String id = nextLongId();
        String result = Long.toString(Long.parseLong(id), 16).toUpperCase();
        return "VID" + getEnvironmentPrefix() + result;
    }
}