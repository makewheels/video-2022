package com.github.makewheels.video2022.openapi.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class UpdateVideoApiRequest {
    @Schema(description = "视频标题", example = "我的第一个视频")
    private String title;
    @Schema(description = "视频描述", example = "这是一个测试视频")
    private String description;
    @Schema(description = "可见性：public/private", example = "public")
    private String visibility;
    @Schema(description = "标签列表", example = "[\"教程\", \"Java\"]")
    private List<String> tags;
    @Schema(description = "分类", example = "教育")
    private String category;
}
