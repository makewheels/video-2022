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
    private String userId;
    @Indexed
    private String videoId;

    //ts视频碎片所属于哪一个转码，它的父亲
    @Indexed
    private String transcodeId;
    //ts碎片，转码所属于哪个分辨率
    @Indexed
    private String resolution;
    //ts碎片，在一个m3u8转码文件中的位置
    @Indexed
    private Integer tsIndex;

    //ts碎片，视频码率
    private Integer bitrate;

    //原始文件名，只有用户上传的视频源文件才有
    private String originalFilename;
    // 63627b7e66445c2fe81c648a.mp4
    private String filename;

    @Indexed
    private String type;

    // videos/62511690c3afe0646f9c670b/63627b7e66445c2fe81c648a/original/63627b7e66445c2fe81c648a.mp4
    private String key;
    private String extension;

    private Long size;
    @Indexed
    private String etag;
    @Indexed
    private String md5;
    private String acl;

    @Indexed
    private String provider;
    @Indexed
    private String videoType;
    @Indexed
    private String storageClass;

    @Indexed
    private String status;
    @Indexed
    private Date createTime;
    @Indexed
    private Date uploadTime;

    @Indexed
    private Boolean isDeleted;

    public File() {
        this.createTime = new Date();
        this.isDeleted = false;
        this.status = FileStatus.CREATED;
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
