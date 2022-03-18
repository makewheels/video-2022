package com.github.makewheels.video2022.redis;

/**
 * @Author makewheels
 * @Time 2021.01.30 13:00:45
 */
public class RedisKey {
    private static final String ROOT = "ums2022";
    private static final String USER = ROOT + ":user";

    public static String verificationCode(String phone) {
        return USER + ":verificationCode:" + phone;
    }

    public static String token(String token) {
        return USER + ":token:" + token;
    }

    public static String userId(String userId) {
        return USER + ":userId:" + userId;
    }

}
