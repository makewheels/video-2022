package com.github.makewheels.video2022.video.bean.dto;

import lombok.Data;

/**
 * 更新播放设置
 */
@Data
public class UpdateWatchSettingsDTO {
    private String id;
    private Boolean showUploadTime;
    private Boolean showWatchCount;
}
