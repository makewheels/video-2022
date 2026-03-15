package com.github.makewheels.video2022.openapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Video Platform Open API")
                        .version("v1")
                        .description("视频平台开放API - 提供视频上传、播放、管理等全部功能\n\n" +
                                "## 快速开始\n\n" +
                                "1. 在[开发者平台](/console/)注册账号\n" +
                                "2. 创建 OAuth 应用获取 `client_id` 和 `client_secret`\n" +
                                "3. 调用 `POST /oauth/token` 获取访问令牌\n" +
                                "4. 在请求头中携带 `Authorization: Bearer <token>` 调用 API\n\n" +
                                "## 认证方式\n\n" +
                                "- `/api/v1/**` 接口：需要 OAuth 2.0 Bearer Token\n" +
                                "- `/developer/**` 接口：需要开发者 JWT 令牌\n" +
                                "- `/oauth/token`：使用 client_id + client_secret 获取 token")
                        .contact(new Contact()
                                .name("Video Platform")
                                .url("https://github.com/makewheels/video-2022")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("OAuth2 Access Token")
                                .description("OAuth 2.0 访问令牌或开发者 JWT 令牌")));
    }
}
