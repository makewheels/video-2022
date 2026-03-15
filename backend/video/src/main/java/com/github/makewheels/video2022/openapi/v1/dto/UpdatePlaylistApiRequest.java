package com.github.makewheels.video2022.openapi.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class UpdatePlaylistApiRequest {
    @Schema(description = "播放列表标题", example = "更新后的标题")
    private String title;
    @Schema(description = "播放列表描述", example = "更新后的描述")
    private String description;
}
