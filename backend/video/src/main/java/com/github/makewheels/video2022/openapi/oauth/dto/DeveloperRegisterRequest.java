package com.github.makewheels.video2022.openapi.oauth.dto;

import lombok.Data;

@Data
public class DeveloperRegisterRequest {
    private String email;
    private String password;
    private String name;
    private String company;
}
