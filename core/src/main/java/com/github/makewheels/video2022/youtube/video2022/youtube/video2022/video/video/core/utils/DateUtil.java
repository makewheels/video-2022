package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.utils;

import java.util.Date;

public class DateUtil {
    public static String getCurrentTimeString() {
        return cn.hutool.core.date.DateUtil.formatDateTime(new Date());
    }
}
