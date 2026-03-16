package com.github.makewheels.video2022.openapi.ratelimit;

/**
 * 令牌桶限流算法实现，线程安全。
 */
public class TokenBucket {
    private final long capacity;
    private final double refillRate; // tokens per second
    private double tokens;
    private long lastRefillTime;

    public TokenBucket(long capacity, double refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = capacity;
        this.lastRefillTime = System.nanoTime();
    }

    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    public synchronized long getRemaining() {
        refill();
        return (long) tokens;
    }

    public long getCapacity() {
        return capacity;
    }

    public synchronized double getSecondsUntilNextToken() {
        refill();
        if (tokens >= 1.0) {
            return 0;
        }
        return (1.0 - tokens) / refillRate;
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsed = (now - lastRefillTime) / 1_000_000_000.0;
        double newTokens = elapsed * refillRate;
        if (newTokens > 0) {
            tokens = Math.min(capacity, tokens + newTokens);
            lastRefillTime = now;
        }
    }
}
