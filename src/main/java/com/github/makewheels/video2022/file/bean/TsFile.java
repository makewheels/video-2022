package com.github.makewheels.video2022.file.bean;

import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FilenameUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document
@Getter
@Setter
public class TsFile extends BasicFile {
    @Id
    private String id;
    @Indexed
    private String uploaderId;
    @Indexed
    private String videoId;

    //ts视频碎片所属于哪一个转码，它的父亲
    @Indexed
    private String transcodeId;
    //ts碎片，转码所属于哪个分辨率
    private String resolution;
    //ts碎片，在一个m3u8转码文件中的位置
    private Integer tsIndex;
    //ts碎片，视频码率
    private Integer bitrate;

    private String fileStatus;

    private String videoType;


    // 单个ts视频时长
    private BigDecimal timeLength;

    public void setObjectInfo(OSSObject object) {
        String key = object.getKey();
        this.setKey(key);
        ObjectMetadata metadata = object.getObjectMetadata();
        this.setEtag(metadata.getETag());
        this.setSize(metadata.getContentLength());
        this.setFilename(FilenameUtils.getName(key));
        this.setExtension(FilenameUtils.getExtension(key));
        this.setStorageClass(metadata.getObjectStorageClass().toString());
        this.setUploadTime(metadata.getLastModified());
    }

    public void setObjectInfo(OSSObjectSummary objectSummary) {
        String key = objectSummary.getKey();
        this.setKey(key);
        this.setEtag(objectSummary.getETag());
        this.setSize(objectSummary.getSize());
        this.setFilename(FilenameUtils.getName(key));
        this.setExtension(FilenameUtils.getExtension(key));
        this.setStorageClass(objectSummary.getStorageClass());
        this.setUploadTime(objectSummary.getLastModified());
    }

}
