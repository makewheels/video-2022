package com.github.makewheels.video2022.openapi.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class DeveloperRegisterRequest {
    @Schema(description = "邮箱地址", example = "developer@example.com")
    private String email;
    @Schema(description = "密码（6位以上）", example = "mypassword123")
    private String password;
    @Schema(description = "开发者姓名", example = "张三")
    private String name;
    @Schema(description = "公司名称（可选）", example = "北京科技有限公司")
    private String company;
}
