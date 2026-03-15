package com.github.makewheels.video2022.openapi.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
public class DeveloperVO {
    @Schema(description = "开发者ID", example = "660a1b2c3d4e5f6789012345")
    private String id;
    @Schema(description = "邮箱", example = "developer@example.com")
    private String email;
    @Schema(description = "姓名", example = "张三")
    private String name;
    @Schema(description = "公司", example = "北京科技有限公司")
    private String company;
    @Schema(description = "账号状态", example = "active")
    private String status;
    @Schema(description = "创建时间")
    private Date createTime;
}
