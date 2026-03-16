package com.github.makewheels.video2022.openapi.ratelimit;

import lombok.Data;

@Data
public class RateLimitResult {
    private boolean allowed;

    private int minuteLimit;
    private long minuteRemaining;
    private long resetTime;

    private int dayLimit;
    private long dayRemaining;

    private long retryAfter;
}
