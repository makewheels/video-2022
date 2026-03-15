package com.github.makewheels.video2022.openapi.webhook.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class WebhookDelivery {
    @Id
    private String id;

    @Indexed
    private String webhookConfigId;

    private String event;
    private String payload;
    private String status;
    private Integer httpStatusCode;
    private String responseBody;
    private Integer attemptCount;

    private Date createTime;
    private Date lastAttemptTime;
}
