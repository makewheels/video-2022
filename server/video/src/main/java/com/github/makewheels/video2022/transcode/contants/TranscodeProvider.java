package com.github.makewheels.video2022.transcode.contants;

public interface TranscodeProvider {
    String ALIYUN_MPS = "ALIYUN_MPS_TRANSCODE";
    /** 旧 CPU 云函数（2022 cfffmpeg 实现），仅保留给历史在途任务排空 */
    String ALIYUN_CLOUD_FUNCTION = "ALIYUN_CLOUD_FUNCTION_TRANSCODE";
    /** 自建 GPU 云函数（NVENC 重编码，新协议） */
    String ALIYUN_CLOUD_FUNCTION_GPU = "ALIYUN_CLOUD_FUNCTION_TRANSCODE_GPU";
    /** 自建 CPU 云函数（探测/截帧/无需重编码的 remux，新协议） */
    String ALIYUN_CLOUD_FUNCTION_CPU = "ALIYUN_CLOUD_FUNCTION_TRANSCODE_CPU";
    /**
     * 本地转码：客户端用本机 FFmpeg 转好 HLS 后回传，服务端只做登记，不调用任何云转码（MPS / 云函数）
     */
    String LOCAL = "LOCAL_TRANSCODE";

}
