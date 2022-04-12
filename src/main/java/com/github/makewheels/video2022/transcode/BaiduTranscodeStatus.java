package com.github.makewheels.video2022.transcode;

import org.apache.commons.lang3.StringUtils;

public class BaiduTranscodeStatus {
    public static final String CREATED = "CREATED";
    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    /**
     * 是不是，已结束的状态
     */
    public static boolean isFinishedStatus(String jobStatus) {
        return StringUtils.equalsAny(jobStatus, SUCCESS, FAILED);
    }
}
