package com.github.makewheels.video2022.transcode.cloudfunction;

/**
 * 云函数转码状态
 */
public class CloudFunctionTranscodeStatus {
    /**
     * 是不是，已结束的状态
     */
    public static boolean isFinishedStatus(String jobStatus) {
        return true;
    }
}
