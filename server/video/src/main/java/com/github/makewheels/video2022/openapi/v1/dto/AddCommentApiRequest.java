package com.github.makewheels.video2022.openapi.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AddCommentApiRequest {
    @Schema(description = "评论内容", example = "这个视频做得很好！")
    private String content;
    @Schema(description = "父评论ID（回复时使用）", example = "660a1b2c3d4e5f6789012345")
    private String parentId;
}
