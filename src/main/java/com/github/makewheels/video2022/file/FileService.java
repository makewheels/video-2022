package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSONObject;
import com.baidubce.Protocol;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.http.HttpMethodName;
import com.baidubce.services.bos.BosClient;
import com.baidubce.services.bos.BosClientConfiguration;
import com.baidubce.services.bos.model.BosObject;
import com.baidubce.services.bos.model.ObjectMetadata;
import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.usermicroservice2022.response.ErrorCode;
import com.github.makewheels.video2022.response.Result;
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
public class FileService {
    @Resource
    private MongoTemplate mongoTemplate;

    @Value("${s3.bucket}")
    private String bucket;
    @Value("${s3.accessKeyId}")
    private String accessKeyId;
    @Value("${s3.secretKey}")
    private String secretKey;

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
                .getPath();
    }

    private BosObject getObject(String key) {
        return getBosClient().getObject(bucket, key);
    }

    public Result<JSONObject> getUploadUrl(User user, String fileId) {
        File file = mongoTemplate.findById(fileId, File.class);
        if (file == null)
            return null;
        if (!StringUtils.equals(user.getId(), file.getUserId()))
            return null;

        String url = getPreSignedUrl(file.getKey(), 30 * 60 * 1000, HttpMethodName.PUT);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("uploadUrl", url);
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
        log.info(JSONObject.toJSONString(bosClient));
        ObjectMetadata objectMetadata = bosObject.getObjectMetadata();
        file.setUploadTime(new Date());
        file.setSize(objectMetadata.getContentLength());
        file.setMd5(objectMetadata.getETag().toLowerCase());
        file.setStatus(FileStatus.READY);
        mongoTemplate.save(file);
        return Result.ok();
    }
}
