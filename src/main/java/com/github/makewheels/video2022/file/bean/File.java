package com.github.makewheels.video2022.file.bean;

import com.alibaba.fastjson.JSON;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.ObjectStorageProvider;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FilenameUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Getter
@Setter
@Document
@JsonIgnoreProperties("objectInfo")
public class File extends BasicFile {
    @Id
    private String id;
    @Indexed
    private String uploaderId;
    @Indexed
    private String videoId;

    // 用户上传的原始文件名，只有 fileType = RAW_VIDEO 才有
    private String rawFilename;

    private String videoType;
    private String fileStatus;

    private Boolean hasLink;  // 是否md5重复，链接到另一个文件
    private String linkFileId;
    private String linkFileKey;

    public File() {
        createTime = new Date();
        deleted = false;
        fileStatus = FileStatus.CREATED;
        provider = ObjectStorageProvider.ALIYUN_OSS;
        hasLink = false;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    public void setObjectInfo(OSSObject object) {
        String key = object.getKey();
        super.key = key;
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
        super.key = key;
        etag = objectSummary.getETag();
        size = objectSummary.getSize();
        filename = FilenameUtils.getName(key);
        extension = FilenameUtils.getExtension(key);
        storageClass = objectSummary.getStorageClass();
        uploadTime = objectSummary.getLastModified();
    }

}
