package com.github.makewheels.video2022.oss.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.comm.Protocol;
import com.aliyun.oss.internal.OSSHeaders;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.CopyObjectRequest;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.StorageClass;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
public class BaseOssService {
    @Getter
    protected String bucket;
    protected String endpoint;
    protected String accessKeyId;
    protected String secretKey;

    protected OSS ossClient;

    /**
     * 获取client
     */
    protected OSS getClient() {
        if (ossClient != null) return ossClient;
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setProtocol(Protocol.HTTPS);
        ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, secretKey, configuration);
        return ossClient;
    }

    /**
     * 获取临时上传凭证
     */
    public JSONObject generateUploadCredentials(String key) {
        DefaultProfile.addEndpoint("cn-beijing", "Sts", "sts.cn-beijing.aliyuncs.com");
        IClientProfile profile = DefaultProfile.getProfile("cn-beijing", accessKeyId, secretKey);
        DefaultAcsClient client = new DefaultAcsClient(profile);
        AssumeRoleRequest request = new AssumeRoleRequest();
        request.setRoleArn("acs:ram::1618784280874658:role/role-oss-video-2022");
        request.setRoleSessionName("roleSessionName-" + IdUtil.simpleUUID());
        request.setDurationSeconds(60 * 60 * 3L);
        AssumeRoleResponse response = null;
        try {
            response = client.getAcsResponse(request);
        } catch (ClientException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }

        JSONObject credentials = new JSONObject();
        credentials.put("bucket", bucket);
        credentials.put("key", key);
        credentials.put("endpoint", endpoint);
        if (response == null) return null;
        AssumeRoleResponse.Credentials responseCredentials = response.getCredentials();
        credentials.put("accessKeyId", responseCredentials.getAccessKeyId());
        credentials.put("secretKey", responseCredentials.getAccessKeySecret());
        credentials.put("sessionToken", responseCredentials.getSecurityToken());
        credentials.put("expiration", responseCredentials.getExpiration());
        return credentials;
    }

    /**
     * 判断object是否存在
     */
    public boolean doesObjectExist(String key) {
        return getClient().doesObjectExist(bucket, key);
    }

    /**
     * 获取单个文件
     */
    public OSSObject getObject(String key) {
        return getClient().getObject(bucket, key);
    }

    /**
     * 按照prefix查找，分页遍历，列举所有文件
     * <a href="https://help.aliyun.com/zh/oss/developer-reference/listobjectsv2">ListObjectsV2文档</a>
     * <a href="https://error-center.aliyun.com/api/Oss/2019-05-17/ListObjectsV2">ListObjectsV2 OpenAPI</a>
     */
    public List<OSSObjectSummary> listAllObjects(String prefix) {
        log.info("阿里云OSS列举所有文件，请求prefix = " + prefix);
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
        log.info("阿里云OSS列举所有文件，返回objects.size() = " + objects.size());
        return objects;
    }

    /**
     * 删除文件
     */
    public void deleteObject(String key) {
        log.info("阿里云OSS删除文件: key = " + key);
        getClient().deleteObject(bucket, key);
    }

    /**
     * 预签名下载文件
     */
    public String generatePresignedUrl(String key, Duration duration) {
        Date expiration = new Date(System.currentTimeMillis() + duration.toMillis());
        return getClient().generatePresignedUrl(bucket, key, expiration, HttpMethod.GET).toString();
    }

    /**
     * 获取临时下载链接
     */
    private String getTempDownloadUrl(String key) {
        return this.generatePresignedUrl(key, Duration.ofHours(1));
    }

    /**
     * 下载文件
     */
    public void downloadFile(String key, File file) {
        log.info("阿里云OSS下载文件: key = {}, file = {}", key, file.getAbsolutePath());
        HttpUtil.downloadFile(this.getTempDownloadUrl(key), file);
    }

    /**
     * 获取文件内容
     */
    public String getObjectContent(String key) {
        log.info("阿里云OSS获取文件内容: key = {}", key);
        return HttpUtil.get(this.getTempDownloadUrl(key));
    }

    /**
     * 设置对象权限
     */
    public void setObjectAcl(String key, CannedAccessControlList cannedAccessControlList) {
        log.info("阿里云OSS设置对象权限, key = {}, cannedAccessControlList = {}", key, cannedAccessControlList);
        getClient().setObjectAcl(bucket, key, cannedAccessControlList);
    }

    /**
     * 改变object存储类型，通过覆盖key实现
     */
    public void changeObjectStorageClass(String key, StorageClass storageClass) {
        log.info("阿里云OSS改变object存储类型, key = {}, storageClass = {}", key, storageClass);
        CopyObjectRequest copyObjectRequest = new CopyObjectRequest(bucket, key, bucket, key);
        ObjectMetadata meta = new ObjectMetadata();
        meta.setHeader(OSSHeaders.OSS_STORAGE_CLASS, storageClass);
        copyObjectRequest.setNewObjectMetadata(meta);
        getClient().copyObject(copyObjectRequest);
    }

}
