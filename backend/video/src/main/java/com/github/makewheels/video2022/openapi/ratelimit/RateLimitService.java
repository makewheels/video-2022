package com.github.makewheels.video2022.openapi.ratelimit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class RateLimitService {

    private static final int STANDARD_LIMIT = 100;
    private static final int PREMIUM_LIMIT = 500;
    private static final long WINDOW_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> requestWindows =
            new ConcurrentHashMap<>();

    /**
     * Check whether the given client is within rate limits.
     *
     * @param clientId unique client identifier (from OAuthContext)
     * @param tier     "standard", "premium", or "unlimited"
     * @return result containing allowed flag, limit, remaining, and reset time
     */
    public RateLimitResult checkRateLimit(String clientId, String tier) {
        int limit = resolveLimit(tier);
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MILLIS;

        ConcurrentLinkedQueue<Long> timestamps = requestWindows
                .computeIfAbsent(clientId, k -> new ConcurrentLinkedQueue<>());

        // Evict expired entries
        while (!timestamps.isEmpty() && timestamps.peek() <= windowStart) {
            timestamps.poll();
        }

        RateLimitResult result = new RateLimitResult();
        result.setLimit(limit);
        result.setResetTime((now / 1000) + 60);

        if (limit < 0) {
            // unlimited tier
            timestamps.add(now);
            result.setAllowed(true);
            result.setRemaining(Integer.MAX_VALUE);
            return result;
        }

        int currentCount = timestamps.size();
        if (currentCount < limit) {
            timestamps.add(now);
            result.setAllowed(true);
            result.setRemaining(limit - currentCount - 1);
        } else {
            result.setAllowed(false);
            result.setRemaining(0);
        }

        return result;
    }

    private int resolveLimit(String tier) {
        if (tier == null) {
            return STANDARD_LIMIT;
        }
        return switch (tier.toLowerCase()) {
            case "premium" -> PREMIUM_LIMIT;
            case "unlimited" -> -1;
            default -> STANDARD_LIMIT;
        };
    }

    /**
     * Periodically remove entries older than the sliding window.
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredWindows() {
        long windowStart = System.currentTimeMillis() - WINDOW_MILLIS;
        Iterator<Map.Entry<String, ConcurrentLinkedQueue<Long>>> it =
                requestWindows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ConcurrentLinkedQueue<Long>> entry = it.next();
            ConcurrentLinkedQueue<Long> timestamps = entry.getValue();
            while (!timestamps.isEmpty() && timestamps.peek() <= windowStart) {
                timestamps.poll();
            }
            if (timestamps.isEmpty()) {
                it.remove();
            }
        }
    }
}
