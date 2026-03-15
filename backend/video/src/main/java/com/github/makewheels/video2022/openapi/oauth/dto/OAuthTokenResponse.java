package com.github.makewheels.video2022.openapi.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class OAuthTokenResponse {
    @Schema(description = "访问令牌", example = "at_abc123def456ghi789")
    private String accessToken;
    @Schema(description = "刷新令牌", example = "rt_xyz789abc123def456")
    private String refreshToken;
    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;
    @Schema(description = "过期时间（秒）", example = "3600")
    private Long expiresIn;
    @Schema(description = "权限范围", example = "video:read video:write")
    private String scope;
}
