package com.github.makewheels.video2022.openapi.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CreateVideoApiRequest {
    @Schema(description = "原始文件名", example = "my-video.mp4")
    private String rawFilename;
    @Schema(description = "文件大小（字节）", example = "104857600")
    private Long size;
    @Schema(description = "视频类型", example = "SHORT")
    private String videoType;
    @Schema(description = "过期时间", example = "PERMANENT")
    private String ttl;
}
