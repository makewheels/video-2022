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
    protected String filename;    // 63627b7e66445c2fe81c648a.mp4
    protected String fileType;

    @Indexed
    protected String key;   // videos/62670b/6362648a/raw/638a.mp4
    protected String extension;
    @Indexed
    protected Long size;
    @Indexed
    protected String etag;
    @Indexed
    protected String md5;
    protected String acl;
    protected String provider;
    protected String storageClass;

    @Indexed
    protected Date uploadTime;
    @Indexed
    protected Date deleteTime;
    @Indexed
    protected Date createTime;
    @Indexed
    protected Date updateTime;

    protected Boolean deleted;

}
