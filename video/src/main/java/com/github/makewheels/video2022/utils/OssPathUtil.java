package com.github.makewheels.video2022.utils;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import com.github.makewheels.video2022.cover.Cover;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.video.bean.entity.Video;

public class OssPathUtil {

    public static String getCoverKey(Video video, Cover cover, File file) {
        return getVideoPrefix(video) + "/cover/" + cover.getId()
                + "/" + file.getId() + "." + cover.getExtension();
    }

    public static String getVideoPrefix(Video video) {
        String createDate = DateUtil.format(video.getCreateTime(), DatePattern.SIMPLE_MONTH_PATTERN);
        return "videos/" + video.getUploaderId() + "/" + createDate + "/" + video.getId();
    }

    public static String getRawFileKey(Video video, File rawFile) {
        return getVideoPrefix(video) + "/raw/" + rawFile.getId()
                + "/" + rawFile.getId() + "." + rawFile.getExtension();
    }

    public static String getTranscodePrefix(Video video) {
        return getVideoPrefix(video) + "/transcode";
    }

    public static String getM3u8Key(Video video, Transcode transcode) {
        return getTranscodePrefix(video) + "/" + transcode.getId() + "/" + transcode.getId() + ".m3u8";
    }
}
