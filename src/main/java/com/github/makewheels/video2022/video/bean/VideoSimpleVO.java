package com.github.makewheels.video2022.video.bean;

import com.alibaba.fastjson.JSON;
import lombok.Data;

import java.util.Date;

@Data
public class VideoSimpleVO {
    private String id;

    private String userId;

    private Integer watchCount;
    private Long duration;
    private String coverUrl;

    private String watchId;
    private String watchUrl;
    private String shortUrl;
    private String title;

    private String type;

    private String youtubeVideoId;
    private String youtubeUrl;
    private Date youtubePublishTime;

    private String status;
    private Date createTime;

    private String createTimeString;
    private String youtubePublishTimeString;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
