package com.github.makewheels.video2022.openapi.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class DeveloperStatsResponse {
    @Schema(description = "应用总数", example = "3")
    private long appCount;
    @Schema(description = "Webhook 配置总数", example = "5")
    private long webhookCount;
    @Schema(description = "累计 API 调用总数", example = "128")
    private long totalApiRequests;
    @Schema(description = "累计 Webhook 投递总数", example = "42")
    private long webhookDeliveryCount;
}
