package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class Video {
    @Id
    private String id;

    @Indexed
    private String userId;
    @Indexed
    private String originalFileId;
    private String originalFileKey;
    @Indexed
    private String watchId;
    private String title;
    private String description;

    @Indexed
    private String status;
    @Indexed
    private Date createTime;

    private JSONObject metaData;
}
