package com.github.makewheels.video2022.openapi.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class CreateOAuthAppResponse {
    @Schema(description = "应用ID", example = "660a1b2c3d4e5f6789012345")
    private String appId;
    @Schema(description = "Client ID（用于认证）", example = "app_abc123def456")
    private String clientId;
    @Schema(description = "Client Secret（仅创建时返回，请妥善保管）", example = "secret_xyz789...")
    private String clientSecret;
    @Schema(description = "应用名称", example = "我的视频应用")
    private String name;
    @Schema(description = "应用描述", example = "用于管理和播放视频内容")
    private String description;
    @Schema(description = "回调地址列表")
    private List<String> redirectUris;
    @Schema(description = "权限范围")
    private List<String> scopes;
    @Schema(description = "速率限制等级", example = "standard")
    private String rateLimitTier;
}
