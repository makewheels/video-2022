package com.github.makewheels.video2022.openapi.oauth.dto;

import lombok.Data;

@Data
public class DeveloperLoginResponse {
    private String token;
    private String developerId;
    private String email;
    private String name;
}
