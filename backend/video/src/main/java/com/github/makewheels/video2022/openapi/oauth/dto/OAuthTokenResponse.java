package com.github.makewheels.video2022.openapi.oauth.dto;

import lombok.Data;

@Data
public class OAuthTokenResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private String scope;
}
