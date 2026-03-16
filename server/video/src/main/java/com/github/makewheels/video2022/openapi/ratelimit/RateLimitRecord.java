package com.github.makewheels.video2022.openapi.ratelimit;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * MongoDB 实体：记录每个 appId 的限流状态快照。
 */
@Data
@Document("rateLimitRecord")
public class RateLimitRecord {
    @Id
    private String id;

    @Indexed(unique = true)
    private String appId;

    private double minuteTokens;
    private double dayTokens;

    private Date lastMinuteRefill;
    private Date lastDayRefill;

    private long totalRequests;

    private Date createTime;
    private Date updateTime;

    public RateLimitRecord() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }

    public RateLimitRecord(String appId, double minuteTokens, double dayTokens) {
        this();
        this.appId = appId;
        this.minuteTokens = minuteTokens;
        this.dayTokens = dayTokens;
        this.lastMinuteRefill = new Date();
        this.lastDayRefill = new Date();
        this.totalRequests = 0;
    }
}
