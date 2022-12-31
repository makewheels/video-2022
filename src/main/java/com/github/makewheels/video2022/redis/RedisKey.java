package com.github.makewheels.video2022.redis;

public class RedisKey {
    private static final String ROOT = "video-2022";

    private static final String USER = ROOT + ":user";
    private static final String VIDEO = ROOT + ":video";

    public static String watchInfo(String watchId) {
        return VIDEO + ":watchInfo:" + watchId;
    }

    public static String ip(String ip) {
        return ROOT + ":ip:" + ip;
    }

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
