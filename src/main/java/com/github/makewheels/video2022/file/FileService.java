package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.usermicroservice2022.response.ErrorCode;
import com.github.makewheels.video2022.response.Result;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import javafx.scene.shape.MoveTo;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Service
public class FileService {
    @Resource
    private MongoTemplate mongoTemplate;

    private String bucket = "video-2022-1253319037";

    private COSClient cosClient;

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

    private COSClient getCOSClient() {
        if (cosClient != null)
            return cosClient;
        String secretId = "AKIDqVv61h7IEvMXVGm22mHXXHm10kFUTDhv";
        String secretKey = "1mJbeRyHK7ewylIZbNt9AwTlNlunQq23";
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setRegion(new Region("ap-beijing"));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        clientConfig.setSocketTimeout(30 * 1000);
        clientConfig.setConnectionTimeout(30 * 1000);
        cosClient = new COSClient(credentials, clientConfig);
        return cosClient;
    }

    private String getPreSignedUrl(String key, long time, HttpMethodName httpMethodName) {
        Date expirationDate = new Date(System.currentTimeMillis() + time);
        return getCOSClient()
                .generatePresignedUrl(bucket, key, expirationDate, httpMethodName)
                .toString();
    }

    private COSObject getObject(String key) {
        return getCOSClient().getObject(bucket, key);
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
        COSObject cosObject = getObject(file.getKey());
        ObjectMetadata objectMetadata = cosObject.getObjectMetadata();
        file.setSize(objectMetadata.getContentLength());
        file.setMd5(objectMetadata.getETag().toLowerCase());
        file.setStatus(FileStatus.READY);
        mongoTemplate.save(file);
        return Result.ok();
    }
}
