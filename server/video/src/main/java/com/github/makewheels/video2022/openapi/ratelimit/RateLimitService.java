package com.github.makewheels.video2022.openapi.ratelimit;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RateLimitService {

    public static final int DEFAULT_MINUTE_LIMIT = 60;
    public static final int DEFAULT_DAY_LIMIT = 10000;
    public static final int IP_MINUTE_LIMIT = 30;
    public static final int IP_DAY_LIMIT = 5000;

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private RateLimitRepository rateLimitRepository;

    private final ConcurrentHashMap<String, TokenBucket[]> buckets = new ConcurrentHashMap<>();

    public RateLimitResult checkRateLimit(String key, boolean isAppAuth) {
        int minuteLimit;
        int dayLimit;
        if (isAppAuth) {
            RateLimitConfig config = loadConfig(key);
            minuteLimit = config != null ? config.getMinuteLimit() : DEFAULT_MINUTE_LIMIT;
            dayLimit = config != null ? config.getDayLimit() : DEFAULT_DAY_LIMIT;
        } else {
            minuteLimit = IP_MINUTE_LIMIT;
            dayLimit = IP_DAY_LIMIT;
        }

        TokenBucket[] pair = buckets.computeIfAbsent(key, k -> new TokenBucket[]{
                new TokenBucket(minuteLimit, minuteLimit / 60.0),
                new TokenBucket(dayLimit, dayLimit / 86400.0)
        });
        TokenBucket minuteBucket = pair[0];
        TokenBucket dayBucket = pair[1];

        RateLimitResult result = new RateLimitResult();
        result.setMinuteLimit(minuteLimit);
        result.setDayLimit(dayLimit);
        result.setResetTime((System.currentTimeMillis() / 1000) + 60);

        boolean minuteOk = minuteBucket.tryConsume();
        boolean dayOk = dayBucket.tryConsume();

        if (minuteOk && dayOk) {
            result.setAllowed(true);
            result.setMinuteRemaining(minuteBucket.getRemaining());
            result.setDayRemaining(dayBucket.getRemaining());
            result.setRetryAfter(0);
        } else {
            result.setAllowed(false);
            result.setMinuteRemaining(minuteBucket.getRemaining());
            result.setDayRemaining(dayBucket.getRemaining());
            long retrySeconds;
            if (!minuteOk) {
                retrySeconds = (long) Math.ceil(minuteBucket.getSecondsUntilNextToken());
            } else {
                retrySeconds = (long) Math.ceil(dayBucket.getSecondsUntilNextToken());
            }
            result.setRetryAfter(Math.max(1, retrySeconds));
        }

        if (isAppAuth) {
            updateRecord(key, minuteBucket, dayBucket);
        }

        return result;
    }

    public RateLimitResult getStatus(String key, boolean isAppAuth) {
        int minuteLimit;
        int dayLimit;
        if (isAppAuth) {
            RateLimitConfig config = loadConfig(key);
            minuteLimit = config != null ? config.getMinuteLimit() : DEFAULT_MINUTE_LIMIT;
            dayLimit = config != null ? config.getDayLimit() : DEFAULT_DAY_LIMIT;
        } else {
            minuteLimit = IP_MINUTE_LIMIT;
            dayLimit = IP_DAY_LIMIT;
        }
        TokenBucket[] pair = buckets.get(key);
        RateLimitResult result = new RateLimitResult();
        result.setAllowed(true);
        result.setMinuteLimit(minuteLimit);
        result.setDayLimit(dayLimit);
        result.setResetTime((System.currentTimeMillis() / 1000) + 60);
        result.setRetryAfter(0);
        if (pair != null) {
            result.setMinuteRemaining(pair[0].getRemaining());
            result.setDayRemaining(pair[1].getRemaining());
        } else {
            result.setMinuteRemaining(minuteLimit);
            result.setDayRemaining(dayLimit);
        }
        return result;
    }

    private RateLimitConfig loadConfig(String appId) {
        Query query = new Query(Criteria.where("appId").is(appId));
        return mongoTemplate.findOne(query, RateLimitConfig.class);
    }

    private void updateRecord(String appId, TokenBucket minuteBucket, TokenBucket dayBucket) {
        try {
            Query query = new Query(Criteria.where("appId").is(appId));
            Update update = new Update()
                    .set("minuteTokens", minuteBucket.getRemaining())
                    .set("dayTokens", dayBucket.getRemaining())
                    .set("lastMinuteRefill", new Date())
                    .set("lastDayRefill", new Date())
                    .inc("totalRequests", 1)
                    .set("updateTime", new Date())
                    .setOnInsert("appId", appId)
                    .setOnInsert("createTime", new Date());
            mongoTemplate.upsert(query, update, RateLimitRecord.class);
        } catch (Exception e) {
            log.warn("更新限流记录失败: appId={}", appId, e);
        }
    }

    public RateLimitRecord getRecord(String appId) {
        return rateLimitRepository.findByAppId(appId);
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupIdleBuckets() {
        buckets.entrySet().removeIf(entry -> {
            TokenBucket[] pair = entry.getValue();
            return pair[0].getRemaining() == pair[0].getCapacity()
                    && pair[1].getRemaining() == pair[1].getCapacity();
        });
    }

    ConcurrentHashMap<String, TokenBucket[]> getBuckets() {
        return buckets;
    }
}
