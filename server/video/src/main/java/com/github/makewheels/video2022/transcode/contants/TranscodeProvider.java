package com.github.makewheels.video2022.transcode.contants;

public interface TranscodeProvider {
    String ALIYUN_MPS = "ALIYUN_MPS_TRANSCODE";
    String ALIYUN_CLOUD_FUNCTION = "ALIYUN_CLOUD_FUNCTION_TRANSCODE";
    String ALIYUN_CLOUD_FUNCTION_GPU = "ALIYUN_CLOUD_FUNCTION_TRANSCODE_GPU";
    /**
     * 本地转码：客户端用本机 FFmpeg 转好 HLS 后回传，服务端只做登记，不调用任何云转码（MPS / 云函数）
     */
    String LOCAL = "LOCAL_TRANSCODE";

}
