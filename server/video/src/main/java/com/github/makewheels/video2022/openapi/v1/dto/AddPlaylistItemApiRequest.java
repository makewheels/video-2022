package com.github.makewheels.video2022.openapi.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AddPlaylistItemApiRequest {
    @Schema(description = "要添加的视频ID", example = "660a1b2c3d4e5f6789012345")
    private String videoId;
}
