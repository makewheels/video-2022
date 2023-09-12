package com.github.makewheels.video2022.file.oss;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * OSS清单和访问日志
 */
@Service
public class OssDataService extends AliyunOssService {
    @Value("${aliyun.oss.data.bucket}")
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    @Value("${aliyun.oss.data.endpoint}")
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    @Value("${aliyun.oss.data.accessKeyId}")
    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    @Value("${aliyun.oss.video.secretKey}")
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
