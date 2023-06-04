package com.github.makewheels.video2022.video.bean.entity;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;

@Data
public class Watch {
    @Indexed
    private String watchId;
    private String watchUrl;
    private String shortUrl;
    private Integer watchCount;         //观看次数

    private Boolean isPlayShowUploadTime; // 前端播放器是否显示上传时间
    private Boolean isPlayShowWatchCount; // 前端播放器是否显示观看次数

    public Watch() {
        this.watchCount = 0;
    }
}
