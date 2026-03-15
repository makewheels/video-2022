package com.github.makewheels.video2022.openapi.oauth.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class OAuthAppVO {
    private String id;
    private String clientId;
    private String name;
    private String description;
    private List<String> redirectUris;
    private List<String> scopes;
    private String rateLimitTier;
    private String status;
    private Date createTime;
}
