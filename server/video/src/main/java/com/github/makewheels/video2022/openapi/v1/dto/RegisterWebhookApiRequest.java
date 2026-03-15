package com.github.makewheels.video2022.openapi.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class RegisterWebhookApiRequest {
    @Schema(description = "Webhook 回调地址", example = "https://myapp.com/webhook")
    private String url;
    @Schema(description = "订阅的事件类型", example = "[\"video.transcode.complete\", \"video.deleted\"]")
    private List<String> events;
}
