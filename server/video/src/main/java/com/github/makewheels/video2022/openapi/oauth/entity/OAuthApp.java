package com.github.makewheels.video2022.openapi.oauth.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Data
@Document
public class OAuthApp {
    @Id
    private String id;

    @Indexed
    private String developerId;

    private String name;
    private String description;

    @Indexed(unique = true)
    private String clientId;

    private String clientSecretHash;

    private List<String> redirectUris;
    private List<String> scopes;

    private String rateLimitTier;
    private String status;

    private Date createTime;
    private Date updateTime;

    public OAuthApp() {
        this.createTime = new Date();
        this.updateTime = new Date();
        this.rateLimitTier = "standard";
        this.status = "active";
    }
}
