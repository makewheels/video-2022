package com.github.makewheels.video2022.openapi.v1.dto;

import lombok.Data;

import java.util.List;

@Data
public class RegisterWebhookApiRequest {
    private String url;
    private List<String> events;
}
