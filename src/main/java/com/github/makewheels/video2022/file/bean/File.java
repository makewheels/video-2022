package com.github.makewheels.video2022.file.bean;

import com.alibaba.fastjson.JSON;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.ObjectStorageProvider;
import lombok.Data;
import org.apache.commons.io.FilenameUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
@JsonIgnoreProperties("objectInfo")
public class File {
    @Id
    private String id;
    @Indexed
    private String uploaderId;
    @Indexed
    private String videoId;

    //原始文件名，只有 fileType = ORIGINAL_VIDEO 才有
    private String originalFilename;
    // 63627b7e66445c2fe81c648a.mp4
    private String filename;
    private String fileType;

    // videos/62511690c3afe0646f9c670b/63627b7e66445c2fe81c648a/original/63627b7e66445c2fe81c648a.mp4
    private String key;
    private String extension;

    @Indexed
    private Long size;
    @Indexed
    private String etag;
    @Indexed
    private String md5;
    private String acl;

    private String provider;
    private String videoType;
    @Indexed
    private String storageClass;
    private String fileStatus;
    @Indexed
    private Date createTime;
    @Indexed
    private Date uploadTime;

    @Indexed
    private Boolean deleted;

    public File() {
        this.createTime = new Date();
        this.deleted = false;
        this.fileStatus = FileStatus.CREATED;
        this.provider = ObjectStorageProvider.ALIYUN_OSS;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    public void setObjectInfo(OSSObject object) {
        String key = object.getKey();
        this.key = key;
        ObjectMetadata metadata = object.getObjectMetadata();
        etag = metadata.getETag();
        size = metadata.getContentLength();
        filename = FilenameUtils.getName(key);
        extension = FilenameUtils.getExtension(key);
        storageClass = metadata.getObjectStorageClass().toString();
        uploadTime = metadata.getLastModified();
    }

    public void setObjectInfo(OSSObjectSummary objectSummary) {
        String key = objectSummary.getKey();
        this.key = key;
        etag = objectSummary.getETag();
        size = objectSummary.getSize();
        filename = FilenameUtils.getName(key);
        extension = FilenameUtils.getExtension(key);
        storageClass = objectSummary.getStorageClass();
        uploadTime = objectSummary.getLastModified();
    }

}
