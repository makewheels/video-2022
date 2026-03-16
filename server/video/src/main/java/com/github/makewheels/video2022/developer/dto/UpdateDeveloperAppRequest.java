package com.github.makewheels.video2022.developer.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateDeveloperAppRequest {
    private String webhookUrl;
    private List<String> webhookEvents;
}
