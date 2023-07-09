package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.redis;

/**
 * Redis缓存过期时间，单位是秒
 */
public interface RedisTime {
    long ONE_MINUTE = 60;
    long TEN_MINUTES = 10 * ONE_MINUTE;
    long THIRTY_MINUTES = 30 * ONE_MINUTE;

    long ONE_HOUR = 60 * ONE_MINUTE;
    long TWO_HOURS = 2 * ONE_HOUR;
    long THREE_HOURS = 3 * ONE_HOUR;
    long SIX_HOURS = 6 * ONE_HOUR;

    long ONE_DAY = 24 * ONE_HOUR;
    long TWO_DAYS = 2 * ONE_DAY;
}
