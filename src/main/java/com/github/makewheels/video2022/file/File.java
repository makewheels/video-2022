package com.github.makewheels.video2022.file;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class File {
    @Id
    private String id;
    @Indexed
    private String userId;

    private String key;
    private String extension;

    private String originalFilename;

    private String accessUrl;
    private String cdnUrl;

    private Long size;
    private String md5;

    @Indexed
    private String provider;
    @Indexed
    private String videoType;

    @Indexed
    private String status;
    @Indexed
    private Date createTime;
    @Indexed
    private Date uploadTime;
}
