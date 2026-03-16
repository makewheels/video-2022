package com.github.makewheels.video2022.openapi.ratelimit;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class RateLimitConfig {
    @Id
    private String id;

    @Indexed(unique = true)
    private String appId;

    private int minuteLimit = 60;
    private int dayLimit = 10000;
    private int burstLimit = 120;

    private Date createTime;
    private Date updateTime;

    public RateLimitConfig() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }
}
