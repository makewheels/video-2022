package com.github.makewheels.video2022.utils;

public class PathUtil {
    public static String getS3VideoPrefix(String userId, String videoId) {
        return "videos/" + userId + "/" + videoId;
    }

    public static String getRawFilePrefix(String userId, String videoId) {
        return getS3VideoPrefix(userId, videoId) + "/raw";
    }

    public static String getS3TranscodePrefix(String userId, String videoId) {
        return getS3VideoPrefix(userId, videoId) + "/transcode";
    }
}
