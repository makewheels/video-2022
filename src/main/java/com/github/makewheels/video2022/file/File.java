package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSONObject;
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

    private Long size;
    private String etag;

    @Indexed
    private String type;

    @Indexed
    private String videoId;

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
