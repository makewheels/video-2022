package com.github.makewheels.video2022.openapi.ratelimit;

import com.github.makewheels.video2022.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest extends BaseIntegrationTest {

    @Autowired
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        rateLimitService.getBuckets().clear();
    }

    @Test
    void tokenBucket_shouldConsumeAndRefill() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(2, 2.0);
        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertFalse(bucket.tryConsume());
        Thread.sleep(600);
        assertTrue(bucket.tryConsume());
    }

    @Test
    void tokenBucket_shouldNotExceedCapacity() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(3, 100.0);
        Thread.sleep(100);
        assertEquals(3, bucket.getRemaining());
    }

    @Test
    void tokenBucket_remainingShouldReflectConsumption() {
        TokenBucket bucket = new TokenBucket(10, 0.0);
        assertEquals(10, bucket.getRemaining());
        bucket.tryConsume();
        assertEquals(9, bucket.getRemaining());
    }

    @Test
    void tokenBucket_secondsUntilNextToken() {
        TokenBucket bucket = new TokenBucket(1, 1.0);
        bucket.tryConsume();
        double wait = bucket.getSecondsUntilNextToken();
        assertTrue(wait > 0 && wait <= 1.0);
    }

    @Test
    void minuteLimit_shouldAllowUpToLimit() {
        String appId = "app-minute-test";
        for (int i = 0; i < RateLimitService.DEFAULT_MINUTE_LIMIT; i++) {
            RateLimitResult result = rateLimitService.checkRateLimit(appId, true);
            assertTrue(result.isAllowed(), "Request " + i + " should be allowed");
        }
    }

    @Test
    void minuteLimit_shouldBlockAfterLimit() {
        String appId = "app-minute-block";
        for (int i = 0; i < RateLimitService.DEFAULT_MINUTE_LIMIT; i++) {
            rateLimitService.checkRateLimit(appId, true);
        }
        RateLimitResult result = rateLimitService.checkRateLimit(appId, true);
        assertFalse(result.isAllowed());
        assertTrue(result.getRetryAfter() > 0);
    }

    @Test
    void minuteLimit_shouldSetCorrectHeaders() {
        String appId = "app-headers";
        RateLimitResult result = rateLimitService.checkRateLimit(appId, true);
        assertEquals(RateLimitService.DEFAULT_MINUTE_LIMIT, result.getMinuteLimit());
        assertEquals(RateLimitService.DEFAULT_DAY_LIMIT, result.getDayLimit());
        assertTrue(result.getResetTime() > 0);
        assertTrue(result.getMinuteRemaining() < RateLimitService.DEFAULT_MINUTE_LIMIT);
    }

    @Test
    void dayLimit_shouldReturnCorrectDefaults() {
        String appId = "app-day-test";
        RateLimitResult result = rateLimitService.checkRateLimit(appId, true);
        assertEquals(RateLimitService.DEFAULT_DAY_LIMIT, result.getDayLimit());
        assertTrue(result.getDayRemaining() > 0);
    }

    @Test
    void concurrentAccess_shouldBeThreadSafe() throws InterruptedException {
        String appId = "app-concurrent";
        int threads = 20;
        int requestsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger denied = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < requestsPerThread; i++) {
                        RateLimitResult result = rateLimitService.checkRateLimit(appId, true);
                        if (result.isAllowed()) {
                            allowed.incrementAndGet();
                        } else {
                            denied.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        int total = allowed.get() + denied.get();
        assertEquals(threads * requestsPerThread, total);
        assertTrue(allowed.get() <= RateLimitService.DEFAULT_MINUTE_LIMIT,
                "Allowed " + allowed.get() + " exceeds limit " + RateLimitService.DEFAULT_MINUTE_LIMIT);
        assertTrue(denied.get() > 0, "Should have denied some requests");
    }

    @Test
    void customLimits_shouldRespectMongoConfig() {
        String appId = "app-custom";
        RateLimitConfig config = new RateLimitConfig();
        config.setAppId(appId);
        config.setMinuteLimit(5);
        config.setDayLimit(100);
        mongoTemplate.save(config);

        for (int i = 0; i < 5; i++) {
            RateLimitResult result = rateLimitService.checkRateLimit(appId, true);
            assertTrue(result.isAllowed());
        }
        RateLimitResult result = rateLimitService.checkRateLimit(appId, true);
        assertFalse(result.isAllowed());
    }

    @Test
    void customLimits_statusShouldReflectConfig() {
        String appId = "app-custom-status";
        RateLimitConfig config = new RateLimitConfig();
        config.setAppId(appId);
        config.setMinuteLimit(10);
        config.setDayLimit(200);
        mongoTemplate.save(config);

        RateLimitResult status = rateLimitService.getStatus(appId, true);
        assertEquals(10, status.getMinuteLimit());
        assertEquals(200, status.getDayLimit());
    }

    @Test
    void ipFallback_shouldUseLowerLimits() {
        String ip = "192.168.1.100";
        RateLimitResult result = rateLimitService.checkRateLimit(ip, false);
        assertTrue(result.isAllowed());
        assertEquals(RateLimitService.IP_MINUTE_LIMIT, result.getMinuteLimit());
        assertEquals(RateLimitService.IP_DAY_LIMIT, result.getDayLimit());
    }

    @Test
    void ipFallback_shouldBlockAfterIpLimit() {
        String ip = "10.0.0.1";
        for (int i = 0; i < RateLimitService.IP_MINUTE_LIMIT; i++) {
            rateLimitService.checkRateLimit(ip, false);
        }
        RateLimitResult result = rateLimitService.checkRateLimit(ip, false);
        assertFalse(result.isAllowed());
    }

    @Test
    void getStatus_shouldNotConsumeTokens() {
        String appId = "app-status-no-consume";
        RateLimitResult status1 = rateLimitService.getStatus(appId, true);
        RateLimitResult status2 = rateLimitService.getStatus(appId, true);
        assertEquals(status1.getMinuteRemaining(), status2.getMinuteRemaining());
    }
}
