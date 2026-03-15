package com.github.makewheels.video2022.openapi.oauth.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Data
@Document
public class OAuthToken {
    @Id
    private String id;

    @Indexed
    private String appId;

    private String userId;

    @Indexed(unique = true)
    private String accessToken;

    private String refreshToken;

    private List<String> scopes;

    private Date expiresAt;
    private Date createTime;

    public OAuthToken() {
        this.createTime = new Date();
    }
}
