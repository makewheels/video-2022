package com.github.makewheels.video2022.redis;

import cn.hutool.core.date.DateUtil;

import java.util.Date;

public class RedisKey {
    private static final String ROOT = "video-2022";

    private static final String USER = ROOT + ":user";
    private static final String VIDEO = ROOT + ":video";
    private static final String TRANSCODE = ROOT + ":transcode";

    private static final String INCREASE_ID = ROOT + ":increaseId";

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


    public static String userCache(String id) {
        return USER + ":id:" + id;
    }

    public static String videoCache(String id) {
        return VIDEO + ":id:" + id;
    }

    public static String transcodeCache(String id) {
        return TRANSCODE + ":id:" + id;
    }

    public static String increaseId() {
        return INCREASE_ID + ":" + DateUtil.formatDate(new Date());
    }

}
