package com.github.makewheels.video2022.redis;

import cn.hutool.core.date.DateUtil;

import java.util.Date;

public class RedisKey {
    private static final String ROOT = "video-2022";

    private static final String USER = ROOT + ":user";
    private static final String VIDEO = ROOT + ":video";
    private static final String TRANSCODE = ROOT + ":transcode";
    private static final String PLAYLIST = ROOT + ":playlist";
    private static final String PLAYLIST_ITEM = ROOT + ":playlistItem";

    private static final String INCREASE_SHORT_ID = ROOT + ":increaseShortId";
    private static final String INCREASE_LONG_ID = ROOT + ":increaseLongId";

    public static String ip(String ip) {
        return ROOT + ":ip:" + ip;
    }

    public static String verificationCode(String phone) {
        return USER + ":verificationCode:" + phone;
    }

    public static String token(String token) {
        return USER + ":token:" + token;
    }

    public static String increaseShortId() {
        return INCREASE_SHORT_ID + ":" + DateUtil.formatDate(new Date());
    }

    public static String increaseLongId(long timeUnit) {
        return INCREASE_LONG_ID + ":" + timeUnit;
    }
}
