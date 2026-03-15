package com.github.makewheels.video2022.openapi.webhook.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Data
@Document
public class WebhookConfig {
    @Id
    private String id;

    @Indexed
    private String appId;

    private String url;
    private List<String> events;
    private String secret;
    private String status;

    private Date createTime;
    private Date updateTime;
}
