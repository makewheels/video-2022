package com.github.makewheels.video2022.openapi.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class DeveloperLoginResponse {
    @Schema(description = "JWT 令牌", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;
    @Schema(description = "开发者ID", example = "660a1b2c3d4e5f6789012345")
    private String developerId;
    @Schema(description = "邮箱", example = "developer@example.com")
    private String email;
    @Schema(description = "姓名", example = "张三")
    private String name;
}
