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
     * <a href="https://help.aliyun.com/zh/oss/developer-reference/listobjectsv2">ListObjectsV2</a>
     * <a href="https://error-center.aliyun.com/api/Oss/2019-05-17/ListObjectsV2">ListObjectsV2</a>
     * {
     *   "ListBucketResult": {
     *     "Name": "oss-data-bucket",
     *     "Prefix": "video-2022-prod/inventory/video-2022-prod/inventory-rule/2023-06-07",
     *     "MaxKeys": "100",
     *     "Delimiter": "",
     *     "IsTruncated": "false",
     *     "Contents": [
     *       {
     *         "Key": "video-2022-prod/inventory/video-2022-prod/inventory-rule/2023-06-07T16-04Z/manifest.checksum",
     *         "LastModified": "2023-06-07T16:04:46.000Z",
     *         "ETag": "\"3E9EE5FC88034DF1489E0C6B283BAFCE\"",
     *         "Type": "Normal",
     *         "Size": "32",
     *         "StorageClass": "Standard"
     *       },
     *       {
     *         "Key": "video-2022-prod/inventory/video-2022-prod/inventory-rule/2023-06-07T16-04Z/manifest.json",
     *         "LastModified": "2023-06-07T16:04:46.000Z",
     *         "ETag": "\"9CD5D7632E6BF479A5FC592462D7025F\"",
     *         "Type": "Normal",
     *         "Size": "530",
     *         "StorageClass": "Standard"
     *       }
     *     ],
     *     "KeyCount": "2"
     *   }
     * }
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
