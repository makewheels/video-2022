package com.github.makewheels.video2022.openapi.oauth.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateOAuthAppResponse {
    private String appId;
    private String clientId;
    private String clientSecret;
    private String name;
    private String description;
    private List<String> redirectUris;
    private List<String> scopes;
    private String rateLimitTier;
}
