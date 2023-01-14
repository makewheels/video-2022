package com.github.makewheels.video2022.redis;

/**
 * Redis缓存过期时间，单位是秒
 */
public interface RedisTime {
    long ONE_MINUTE = 60;
    long TEN_MINUTES = 10 * 60;
    long THIRTY_MINUTES = 30 * 60;

    long ONE_HOUR = 60 * 60;
    long TWO_HOURS = 2 * 60 * 60;
    long THREE_HOURS = 3 * 60 * 60;
    long SIX_HOURS = 6 * 60 * 60;

}
