package com.github.makewheels.video2022.redis;

public class RedisKey {
    private static final String ROOT = "video-2022";
    private static final String VIDEO = ROOT + ":video";

    public static String watchInfo(String watchId) {
        return VIDEO + ":watchInfo:" + watchId;
    }

    public static String ip(String ip) {
        return ROOT + ":ip:" + ip;
    }

}
