package com.github.makewheels.video2022.redis;

/**
 * @Author makewheels
 * @Time 2021.01.30 13:00:45
 */
public class RedisKey {
    private static final String ROOT = "video-2022";
    private static final String VIDEO = ROOT + ":video";

    public static String watchInfo(String watchId) {
        return VIDEO + ":watchInfo:" + watchId;
    }

}
