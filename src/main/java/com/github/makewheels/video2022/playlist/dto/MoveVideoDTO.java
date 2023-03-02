package com.github.makewheels.video2022.playlist.dto;

import lombok.Data;

@Data
public class MoveVideoDTO {
    private String playlistId;
    private String videoId;

    // 移动模式：
    // MOVE_TO_POSITION  移到指定索引位置
    // MOVE_BEFORE_VIDEO 移到指定视频之前
    // MOVE_AFTER_VIDEO  移到指定视频之后
    private String mode;

    private Integer toPosition;
    private String toVideoId;
}
