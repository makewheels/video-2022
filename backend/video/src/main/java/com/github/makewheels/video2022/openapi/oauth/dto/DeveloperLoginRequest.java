package com.github.makewheels.video2022.openapi.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class DeveloperLoginRequest {
    @Schema(description = "邮箱地址", example = "developer@example.com")
    private String email;
    @Schema(description = "密码", example = "mypassword123")
    private String password;
}
