package com.github.makewheels.video2022.developer.dto;

import lombok.Data;

@Data
public class CreateTokenRequest {
    private String appId;
    private String appSecret;
}
