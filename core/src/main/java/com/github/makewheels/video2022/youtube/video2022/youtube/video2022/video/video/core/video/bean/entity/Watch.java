package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.video.bean.entity;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;

/**
 * 视频播放信息
 */
@Data
public class Watch {
    public static final String FIELD_NAME = "watch";

    @Indexed
    private String watchId;
    private String watchUrl;
    private String shortUrl;
    private Integer watchCount;         //观看次数

    private Boolean showUploadTime; // 前端播放器是否显示上传时间
    private Boolean showWatchCount; // 前端播放器是否显示观看次数

    public Watch() {
        this.watchCount = 0;
        this.showUploadTime = true;
        this.showWatchCount = true;
    }
}
