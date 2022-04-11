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

    private Integer watchCount;
    private Integer duration;
    private String coverUrl;

    @Indexed
    private String watchId;
    private String watchUrl;
    private String shortUrl;
    private String title;
    private String description;

    private Integer width;
    private Integer height;

    @Indexed
    private String type;
    @Indexed
    private String provider;

    @Indexed
    private String youtubeVideoId;
    private String youtubeUrl;

    @Indexed
    private String status;
    @Indexed
    private Date createTime;

    private JSONObject mediaInfo;

    private JSONObject youtubeVideoInfo;
}
