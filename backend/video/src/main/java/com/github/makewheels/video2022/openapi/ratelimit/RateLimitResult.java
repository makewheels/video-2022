package com.github.makewheels.video2022.openapi.ratelimit;

import lombok.Data;

@Data
public class RateLimitResult {
    private boolean allowed;
    private int limit;
    private int remaining;
    private long resetTime; // epoch seconds
}
