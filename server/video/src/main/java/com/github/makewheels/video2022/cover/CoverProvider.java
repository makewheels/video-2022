package com.github.makewheels.video2022.cover;

public class CoverProvider {
    public static final String YOUTUBE = "YOUTUBE_COVER";
    public static final String ALIYUN_MPS = "ALIYUN_MPS_SNAPSHOT";
    public static final String ALIYUN_CLOUD_FUNCTION = "ALIYUN_CLOUD_FUNCTION_COVER";
    /**
     * 本地截帧：客户端用本机 FFmpeg 截好封面后回传，服务端只做登记
     */
    public static final String LOCAL = "LOCAL_COVER";
}
