package com.github.makewheels.video2022.video.bean;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.video.constants.VideoType;
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
    private Long duration;
    private String coverId;
    private String coverUrl;

    @Indexed
    private String watchId;
    private String watchUrl;
    private String shortUrl;
    private String title;
    private String description;

    private Integer width;
    private Integer height;
    private String videoCodec;
    private String audioCodec;
    private Integer bitrate;

    @Indexed
    private String type;
    @Indexed
    private String provider;    //它就是对象存储提供商，和file是一对一关系

    @Indexed
    private String youtubeVideoId;
    private String youtubeUrl;
    private JSONObject youtubeVideoInfo;
    private Date youtubePublishTime;

    @Indexed
    private String status;
    @Indexed
    private Date createTime;

    @Indexed
    private Date expireTime;
    @Indexed
    private Boolean isPermanent;    //是否是永久视频
    private Boolean isFilesDeleted; //是否已删除
    private Date deleteTime;        //什么时候删的

    private JSONObject mediaInfo;

    public boolean isYoutube() {
        return type.equals(VideoType.YOUTUBE);
    }
}
