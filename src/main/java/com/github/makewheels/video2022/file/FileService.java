package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSONObject;
import com.baidubce.BceClientConfiguration;
import com.baidubce.Protocol;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.http.HttpMethodName;
import com.baidubce.services.bos.BosClient;
import com.baidubce.services.bos.BosClientConfiguration;
import com.baidubce.services.bos.model.BosObject;
import com.baidubce.services.bos.model.ObjectMetadata;
import com.baidubce.services.sts.StsClient;
import com.baidubce.services.sts.model.GetSessionTokenRequest;
import com.baidubce.services.sts.model.GetSessionTokenResponse;
import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.usermicroservice2022.response.ErrorCode;
import com.github.makewheels.video2022.response.Result;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Service
@Slf4j
@Data
public class FileService {
    @Resource
    private MongoTemplate mongoTemplate;

    @Value("${s3.bucket}")
    private String bucket;
    @Value("${s3.endpoint}")
    private String endpoint;
    @Value("${s3.accessKeyId}")
    private String accessKeyId;
    @Value("${s3.secretKey}")
    private String secretKey;
    @Value("${s3.accessBaseUrl}")
    private String accessBaseUrl;
    @Value("${s3.cdnBaseUrl}")
    private String cdnBaseUrl;

    private BosClient bosClient;

    public File create(User user, String originalFilename) {
        File file = new File();
        file.setUserId(user.getId());
        file.setOriginalFilename(originalFilename);
        file.setExtension(FilenameUtils.getExtension(originalFilename).toLowerCase());
        file.setStatus(FileStatus.CREATED);
        file.setCreateTime(new Date());
        mongoTemplate.save(file);
        return file;
    }

    private BosClient getBosClient() {
        if (bosClient != null) {
            return bosClient;
        }
        BosClientConfiguration config = new BosClientConfiguration();
        config.setCredentials(new DefaultBceCredentials(accessKeyId, secretKey));
        config.setEndpoint("bj.bcebos.com");
        config.setProtocol(Protocol.HTTPS);
        // 设置HTTP最大连接数为10
        config.setMaxConnections(10);
        // 设置TCP连接超时为5000毫秒
        config.setConnectionTimeoutInMillis(5000);
        // 设置Socket传输数据超时的时间为2000毫秒
        config.setSocketTimeoutInMillis(2000);
        // 设置PUT操作为同步方式，默认异步
        config.setEnableHttpAsyncPut(false);
        bosClient = new BosClient(config);
        return bosClient;
    }

    /**
     * 生成预签名url
     *
     * @param key
     * @param time           有效时长，单位毫秒
     * @param httpMethodName
     * @return
     */
    private String getPreSignedUrl(String key, long time, HttpMethodName httpMethodName) {
        return getBosClient().generatePresignedUrl(
                        bucket, key, (int) (time / 1000), httpMethodName)
                .toString();
    }

    private BosObject getObject(String key) {
        return getBosClient().getObject(bucket, key);
    }

    public Result<JSONObject> getUploadCredentials(User user, String fileId) {
        File file = mongoTemplate.findById(fileId, File.class);
        if (file == null) return null;
        if (!StringUtils.equals(user.getId(), file.getUserId())) return null;

        StsClient stsClient = new StsClient(new BceClientConfiguration().withEndpoint("https://sts.bj.baidubce.com")
                .withCredentials(new DefaultBceCredentials(accessKeyId, secretKey)));
        GetSessionTokenResponse response = stsClient.getSessionToken(
                new GetSessionTokenRequest().withDurationSeconds(3 * 60 * 60));

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("bucket", bucket);
        jsonObject.put("key", file.getKey());
        if (endpoint.startsWith("http")) {
            jsonObject.put("endpoint", endpoint);
        } else {
            jsonObject.put("endpoint", "https://" + endpoint);
        }
        jsonObject.put("accessKeyId", response.getAccessKeyId());
        jsonObject.put("secretKey", response.getSecretAccessKey());
        jsonObject.put("sessionToken", response.getSessionToken());
        return Result.ok(jsonObject);
    }

    public Result<Void> uploadFinish(User user, String fileId) {
        File file = mongoTemplate.findById(fileId, File.class);
        if (file == null)
            return Result.error(ErrorCode.FAIL);
        if (!StringUtils.equals(user.getId(), file.getUserId()))
            return Result.error(ErrorCode.FAIL);
        BosObject bosObject = getObject(file.getKey());
        log.info("文件上传完成，fileId = " + fileId);
        log.info(JSONObject.toJSONString(bosObject));
        ObjectMetadata objectMetadata = bosObject.getObjectMetadata();
        file.setUploadTime(new Date());
        file.setSize(objectMetadata.getContentLength());
        file.setMd5(objectMetadata.getETag().toLowerCase());
        file.setStatus(FileStatus.READY);
        mongoTemplate.save(file);
        return Result.ok();
    }
}
