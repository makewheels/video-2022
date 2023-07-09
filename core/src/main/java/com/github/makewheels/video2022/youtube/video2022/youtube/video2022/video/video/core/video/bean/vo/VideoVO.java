package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.video.bean.vo;

import com.alibaba.fastjson.JSON;
import lombok.Data;

import java.util.Date;

@Data
public class VideoVO {
    private String id;

    private String userId;
    private Integer watchCount;
    private Long duration;
    private String coverUrl;
    private String watchId;
    private String watchUrl;
    private String shortUrl;
    private String title;
    private String description;
    private String type;
    private String status;

    private Date createTime;
    private String createTimeString;

    private Date youtubePublishTime;
    private String youtubePublishTimeString;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
