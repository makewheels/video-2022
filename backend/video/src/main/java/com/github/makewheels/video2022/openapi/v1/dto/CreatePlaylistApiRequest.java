package com.github.makewheels.video2022.openapi.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CreatePlaylistApiRequest {
    @Schema(description = "播放列表标题", example = "我的收藏")
    private String title;
    @Schema(description = "播放列表描述", example = "精选视频合集")
    private String description;
}
