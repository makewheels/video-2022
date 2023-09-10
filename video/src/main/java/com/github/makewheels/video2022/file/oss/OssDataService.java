package com.github.makewheels.video2022.file.oss;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.comm.Protocol;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObjectSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * OSS清单和访问日志
 */
@Service
public class OssDataService extends AliyunOssService {
    @Value("${aliyun.oss.data.bucket}")
    private String bucket;
    @Value("${aliyun.oss.data.endpoint}")
    private String endpoint;
    @Value("${aliyun.oss.data.accessKeyId}")
    private String accessKeyId;
    @Value("${aliyun.oss.video.secretKey}")
    private String secretKey;

    private OSS ossClient;

    /**
     * 获取client
     */
    private OSS getClient() {
        if (ossClient != null) return ossClient;
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setProtocol(Protocol.HTTPS);
        ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, secretKey, configuration);
        return ossClient;
    }

    /**
     * 按照prefix查找，分页遍历，列举所有文件
     */
    public List<OSSObjectSummary> listAllObjects(String prefix) {
        List<OSSObjectSummary> objects = new ArrayList<>();
        String nextContinuationToken = null;
        ListObjectsV2Result result;
        do {
            ListObjectsV2Request listObjectsRequest = new ListObjectsV2Request();
            listObjectsRequest.setBucketName(bucket);
            listObjectsRequest.withMaxKeys(1000);
            listObjectsRequest.setContinuationToken(nextContinuationToken);
            listObjectsRequest.setPrefix(prefix);
            result = getClient().listObjectsV2(listObjectsRequest);
            objects.addAll(result.getObjectSummaries());
            nextContinuationToken = result.getNextContinuationToken();
        } while (result.isTruncated());
        return objects;
    }
}
