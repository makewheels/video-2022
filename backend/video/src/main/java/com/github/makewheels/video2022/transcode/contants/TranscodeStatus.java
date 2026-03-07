package com.github.makewheels.video2022.transcode.contants;

import com.github.makewheels.video2022.transcode.aliyun.AliyunTranscodeStatus;

public class TranscodeStatus {
    public static final String FINISHED = "FINISHED";

    /**
     * 判断：是不是，已结束的状态
     */
    public static boolean isFinishStatus(String status) {
        return status.equals(FINISHED) || AliyunTranscodeStatus.isFinishStatus(status);
    }
}
