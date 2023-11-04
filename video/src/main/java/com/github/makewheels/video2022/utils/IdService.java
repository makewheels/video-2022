package com.github.makewheels.video2022.utils;

import cn.hutool.core.util.RandomUtil;
import com.github.makewheels.video2022.etc.redis.RedisKey;
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

    private synchronized String nextId(
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
        log.debug("生成id: " + timeUnit + "-" + serialNumber + "-" + random);

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
        log.debug("生成id：format = {}, result = {}", format, result);

        return result;
    }

    private synchronized String getCommonId() {
        return nextLongId();
    }

    public synchronized String nextLongId(String prefix) {
        return prefix + getCommonId();
    }

    public synchronized String getUserId() {
        return "u_" + getCommonId();
    }

    public synchronized String getVideoId() {
        return "v_" + getCommonId();
    }

    public synchronized String getTranscodeId() {
        return "tr_" + getCommonId();
    }

    public synchronized String getCoverId() {
        return "c_" + getCommonId();
    }

    public synchronized String getFileId() {
        return "f_" + getCommonId();
    }

    public synchronized String getTsFileId() {
        return "f_ts_" + getCommonId();
    }

}
