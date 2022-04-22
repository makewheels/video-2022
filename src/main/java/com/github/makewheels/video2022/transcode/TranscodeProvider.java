package com.github.makewheels.video2022.transcode;

import com.github.makewheels.video2022.file.S3Provider;

public class TranscodeProvider {
    public static final String ALIYUN_MPS = "ALIYUN_MPS";
    public static final String BAIDU_MCP = "BAIDU_MCP";
    public static final String ALIYUN_CLOUD_FUNCTION = "ALIYUN_CLOUD_FUNCTION";

    /**
     * 根据对象存储provider返回对应云服务商的视频转码provider
     *
     * @param s3Provider
     * @return
     */
    public static String getByS3Provider(String s3Provider) {
        if (s3Provider.equals(S3Provider.ALIYUN_OSS)) {
            return ALIYUN_MPS;
        } else if (s3Provider.equals(S3Provider.BAIDU_BOS)) {
            return BAIDU_MCP;
        }
        return null;
    }
}
