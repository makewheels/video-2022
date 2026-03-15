package com.github.makewheels.video2022.transcode.aliyun;

import org.apache.commons.lang3.StringUtils;

public class AliyunTranscodeStatus {
    public static final String Submitted = "Submitted";
    public static final String Transcoding = "Transcoding";
    public static final String TranscodeSuccess = "TranscodeSuccess";
    public static final String TranscodeFail = "TranscodeFail";
    public static final String TranscodeCancelled = "TranscodeCancelled";

    /**
     * 判断：是不是，已结束的状态
     */
    public static boolean isFinishStatus(String jobStatus) {
        return StringUtils.equalsAny(jobStatus, TranscodeSuccess, TranscodeFail, TranscodeCancelled);
    }
}
