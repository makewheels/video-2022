package com.github.makewheels.video2022.file;

import com.alibaba.fastjson2.JSONObject;
import com.baidubce.BceClientConfiguration;
import com.baidubce.Protocol;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.services.bos.BosClient;
import com.baidubce.services.bos.BosClientConfiguration;
import com.baidubce.services.bos.model.BosObject;
import com.baidubce.services.sts.StsClient;
import com.baidubce.services.sts.model.GetSessionTokenRequest;
import com.baidubce.services.sts.model.GetSessionTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BaiduBosService {
    @Value("${baidu.bos.bucket}")
    private String bucket;
    @Value("${baidu.bos.endpoint}")
    private String endpoint;
    @Value("${baidu.bos.accessKeyId}")
    private String accessKeyId;
    @Value("${baidu.bos.secretKey}")
    private String secretKey;

    private BosClient client;

    /**
     * 普通客户端
     *
     * @return
     */
    private BosClient getClient() {
        if (client != null) return client;
        BosClientConfiguration config = new BosClientConfiguration();
        config.setCredentials(new DefaultBceCredentials(accessKeyId, secretKey));
        config.setEndpoint("bj.bcebos.com");
        config.setProtocol(Protocol.HTTPS);
        // 设置PUT操作为同步方式，默认异步
        config.setEnableHttpAsyncPut(false);
        client = new BosClient(config);
        return client;
    }

    public BosObject getObject(String key) {
        return getClient().getObject(bucket, key);
    }

    public JSONObject getUploadCredentials(String key) {
        StsClient stsClient = new StsClient(
                new BceClientConfiguration().withEndpoint("https://sts.bj.baidubce.com")
                        .withCredentials(new DefaultBceCredentials(accessKeyId, secretKey)));
        GetSessionTokenResponse response = stsClient.getSessionToken(
                new GetSessionTokenRequest().withDurationSeconds(60 * 60 * 3));
        JSONObject credentials = new JSONObject();
        credentials.put("bucket", bucket);
        credentials.put("key", key);
        if (endpoint.startsWith("http")) {
            credentials.put("endpoint", endpoint);
        } else {
            credentials.put("endpoint", "https://" + endpoint);
        }
        credentials.put("accessKeyId", response.getAccessKeyId());
        credentials.put("secretKey", response.getSecretAccessKey());
        credentials.put("sessionToken", response.getSessionToken());
        credentials.put("expiration", response.getExpiration());
        return credentials;
    }
}
