package com.github.makewheels.video2022.video;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

@Data
public class VideoInfo {
    @Id
    private String id;

    @Indexed
    private String userId;

    private Integer watchCount;
    private Integer duration;

    @Indexed
    private String watchId;
    private String watchUrl;
    private String title;
    private String description;

    @Indexed
    private String status;
    @Indexed
    private Date createTime;

    private String createTimeString;

    private String coverUrl;

}
