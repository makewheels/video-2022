package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.playlist.item;

import lombok.Data;

@Data
public class PlayItemVO {
    private String playItemId;
    private String videoId;
    private String videoCreateTime;
    private String videoUpdateTime;

    private String title;
    private Long duration;
    private String coverUrl;
    private Integer watchCount;

    private String watchId;
    private String watchUrl;
    private String shortUrl;

    private String videoStatus;

}
