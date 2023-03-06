package com.github.makewheels.video2022.playlist.item;

import lombok.Data;

import java.util.Date;

@Data
public class PlayItemVO {
    private String playItemId;
    private String videoId;
    private Date videoCreateTime;
    private Date videoUpdateTime;

    private String title;
    private Long duration;
    private String coverUrl;

    private String watchId;
    private String watchUrl;
    private String shortUrl;

    private String videoStatus;

}
