package com.github.makewheels.video2022.file;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.comm.Protocol;
import com.aliyun.oss.model.OSSObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AliyunOssService {
    @Value("${aliyun.oss.bucket}")
    private String bucket;
    @Value("${aliyun.oss.endpoint}")
    private String endpoint;
    @Value("${aliyun.oss.accessKeyId}")
    private String accessKeyId;
    @Value("${aliyun.oss.secretKey}")
    private String secretKey;

    private OSS ossClient;

    public OSS getClient() {
        if (ossClient != null) return ossClient;
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setProtocol(Protocol.HTTPS);
        ossClient = new OSSClientBuilder().build(endpoint,
                Base64.decodeStr(accessKeyId), Base64.decodeStr(secretKey),
                configuration);
        return ossClient;
    }

    public OSSObject getObject(String key) {
        return getClient().getObject(bucket, key);
    }

    public boolean doesObjectExist(String key) {
        return getClient().doesObjectExist(bucket, key);

    }

    public JSONObject getUploadCredentials(String key) {
        DefaultProfile.addEndpoint("cn-beijing", "Sts", "sts.cn-beijing.aliyuncs.com");
        IClientProfile profile = DefaultProfile.getProfile("cn-beijing",
                Base64.decodeStr(accessKeyId), Base64.decodeStr(secretKey));
        DefaultAcsClient client = new DefaultAcsClient(profile);
        AssumeRoleRequest request = new AssumeRoleRequest();
        //????????????????????????
//        request.setPolicy("");
        request.setRoleArn("acs:ram::1618784280874658:role/role-oss-video-2022");
        request.setRoleSessionName("roleSessionName-" + IdUtil.simpleUUID());
        request.setDurationSeconds(60 * 60 * 3L);
        AssumeRoleResponse response = null;
        try {
            response = client.getAcsResponse(request);
        } catch (ClientException e) {
            e.printStackTrace();
        }

        JSONObject credentials = new JSONObject();
        credentials.put("bucket", bucket);
        credentials.put("key", key);
//        if (endpoint.startsWith("http")) {
        credentials.put("endpoint", endpoint);
//        } else {
//            credentials.put("endpoint", "https://" + endpoint);
//        }
        if (response == null) return null;
        AssumeRoleResponse.Credentials responseCredentials = response.getCredentials();
        credentials.put("accessKeyId", responseCredentials.getAccessKeyId());
        credentials.put("secretKey", responseCredentials.getAccessKeySecret());
        credentials.put("sessionToken", responseCredentials.getSecurityToken());
        credentials.put("expiration", responseCredentials.getExpiration());
        return credentials;
    }
}
