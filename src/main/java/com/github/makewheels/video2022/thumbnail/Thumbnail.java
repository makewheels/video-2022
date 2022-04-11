package com.github.makewheels.video2022.thumbnail;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class Thumbnail {
    @Id
    private String id;
    @Indexed
    private String userId;

    @Indexed
    private String videoId;

    @Indexed
    private String provider;

    @Indexed
    private String jobId;
    @Indexed
    private Date createTime;
    @Indexed
    private Date finishTime;
    @Indexed
    private String status;

    private String sourceKey;
    private String targetKeyPrefix;

    private String accessUrl;
    private String cdnUrl;

    private String extension;
    private String key;

    private JSONObject result;
}
