package com.github.makewheels.video2022.transcode;

import com.github.makewheels.video2022.file.constants.S3Provider;

public class TranscodeProvider {
    public static final String ALIYUN_MPS = "ALIYUN_MPS_TRANSCODE";
    public static final String ALIYUN_CLOUD_FUNCTION = "ALIYUN_CLOUD_FUNCTION_TRANSCODE";

    /**
     * 根据对象存储provider返回对应云服务商的视频转码provider
     */
    public static String getByS3Provider(String s3Provider) {
        if (s3Provider.equals(S3Provider.ALIYUN_OSS)) {
            return ALIYUN_MPS;
        }
        return null;
    }
}
