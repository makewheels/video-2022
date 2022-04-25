package com.github.makewheels.video2022.video;

import lombok.Data;

import java.util.Date;

@Data
public class VideoInfo {
    private String id;

    private String userId;

    private Integer watchCount;
    private Long duration;

    private String watchId;
    private String watchUrl;
    private String title;
    private String description;

    private String type;
    private String status;
    private Date createTime;

    private String createTimeString;

    private String coverUrl;

}
