package com.github.makewheels.video2022.developer.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@Document(collection = "developerApp")
public class DeveloperApp {
    @Id
    private String id;

    private String appName;

    @Indexed(unique = true)
    private String appId;

    private String appSecret;

    @Indexed
    private String userId;

    private String webhookUrl;
    private String webhookSecret;
    private List<String> webhookEvents = new ArrayList<>();

    private String status;

    private Date createdAt;
    private Date updatedAt;

    public DeveloperApp() {
        this.status = "active";
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }
}
