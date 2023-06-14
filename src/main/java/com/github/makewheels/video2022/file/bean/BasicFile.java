package com.github.makewheels.video2022.file.bean;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

/**
 * 文件基本信息，父类用于继承
 */
@Getter
@Setter
public class BasicFile {
    @Indexed
    private String filename;    // 63627b7e66445c2fe81c648a.mp4
    private String fileType;

    @Indexed
    private String key;   // videos/62670b/6362648a/original/638a.mp4
    private String extension;
    @Indexed
    private Long size;
    @Indexed
    private String etag;
    @Indexed
    private String md5;
    private String acl;
    private String provider;
    private String storageClass;

    @Indexed
    private Date createTime;
    @Indexed
    private Date uploadTime;
    private Boolean deleted;

}
