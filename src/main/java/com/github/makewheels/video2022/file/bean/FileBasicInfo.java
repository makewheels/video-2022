package com.github.makewheels.video2022.file.bean;

import lombok.Data;

import java.util.Date;

/**
 * 文件基本信息，父类用于继承
 */
@Data
public class FileBasicInfo {
    private String filename;    // 63627b7e66445c2fe81c648a.mp4
    private String type;
    private String extension;
    private Long size;
    private String provider;
    private Date createTime;
    private Date uploadTime;
    private Boolean isDeleted;

    // 阿里云oss信息
    private String key;  // videos/62670b/6362648a/original/638a.mp4
    private String etag;
    private String md5;
    private String storageClass;
    private String acl;

}
