package com.github.makewheels.video2022.utils;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.video.bean.entity.Video;

public class PathUtil {
    public static String getS3VideoPrefix(String userId, String videoId) {
        return "videos/" + userId + "/" + videoId;
    }

    public static String getRawFilePrefix(String userId, String videoId) {
        return getS3VideoPrefix(userId, videoId) + "/raw";
    }

    public static String getRawFilePrefix(Video video, File rawFile) {
        String createDate = DateUtil.format(video.getCreateTime(), DatePattern.SIMPLE_MONTH_PATTERN);
        return "videos/" + video.getUploaderId() + "/" + createDate + "/" + video.getId()
                + "/raw/" + rawFile.getId() + "." + rawFile.getExtension();
    }

    public static String getS3TranscodePrefix(String userId, String videoId) {
        return getS3VideoPrefix(userId, videoId) + "/transcode";
    }
}
