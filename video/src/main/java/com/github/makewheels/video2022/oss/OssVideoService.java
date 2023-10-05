package com.github.makewheels.video2022.oss;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OssVideoService extends AliyunOssService {
    @Value("${aliyun.oss.video.bucket}")
    private void setBucket(String bucket) {
        super.bucket = bucket;
    }

    @Value("${aliyun.oss.video.endpoint}")
    private void setEndpoint(String endpoint) {
        super.endpoint = endpoint;
    }

    @Value("${aliyun.oss.video.accessKeyId}")
    private void setAccessKeyId(String accessKeyId) {
        super.accessKeyId = accessKeyId;
    }

    @Value("${aliyun.oss.video.secretKey}")
    private void setSecretKey(String secretKey) {
        super.secretKey = secretKey;
    }
}
