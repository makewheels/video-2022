package com.github.makewheels.video2022.openapi.oauth.dto;

import lombok.Data;

@Data
public class DeveloperLoginRequest {
    private String email;
    private String password;
}
