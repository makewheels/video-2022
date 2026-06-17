package com.github.makewheels.video2022.video.constants;

/**
 * 转码方式
 */
public class TranscodeMode {
    /**
     * 云端转码（默认）：服务端按源片自动选择 阿里云 MPS 或 云函数
     */
    public static final String AUTO = "AUTO";
    /**
     * 本地转码：客户端用本机 FFmpeg 转好 HLS 后回传，服务端不发起任何云转码
     */
    public static final String LOCAL = "LOCAL";

    public static boolean isLocal(String transcodeMode) {
        return LOCAL.equals(transcodeMode);
    }
}
