package com.github.makewheels.video2022.openapi.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class CreateOAuthAppRequest {
    @Schema(description = "应用名称", example = "我的视频应用")
    private String name;
    @Schema(description = "应用描述", example = "用于管理和播放视频内容")
    private String description;
    @Schema(description = "回调地址列表", example = "[\"https://myapp.com/callback\"]")
    private List<String> redirectUris;
    @Schema(description = "权限范围", example = "[\"video:read\", \"video:write\"]")
    private List<String> scopes;
}
