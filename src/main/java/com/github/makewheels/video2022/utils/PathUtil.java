package com.github.makewheels.video2022.utils;

public class PathUtil {
    public static String getS3VideoPrefix(String userId, String videoId) {
        return "videos/" + userId + "/" + videoId;
    }
}
