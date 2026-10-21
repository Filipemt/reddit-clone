package com.motadev.clone_reddit.shared.ratelimit.store;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RedisRateLimitStore implements RateLimitStore {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisRateLimitStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public RateLimitDecision consume(String bucketKey, int limit, int windowSeconds) {
        Long count = stringRedisTemplate.opsForValue().increment(bucketKey);
        if (count == null) {
            count = 1L;
        }
        if (count == 1L) {
            stringRedisTemplate.expire(bucketKey, Duration.ofSeconds(windowSeconds));
        }

        Long ttlSeconds = stringRedisTemplate.getExpire(bucketKey, TimeUnit.SECONDS);
        long retryAfter = resolveRetryAfter(ttlSeconds, windowSeconds);
        long resetEpochSeconds = (System.currentTimeMillis() / 1000L) + retryAfter;
        int remaining = (int) Math.max(0L, limit - count);

        return new RateLimitDecision(count <= limit, limit, remaining, resetEpochSeconds, retryAfter);
    }

    private static long resolveRetryAfter(Long ttlSeconds, int windowSeconds) {
        if (ttlSeconds == null || ttlSeconds < 0) {
            return windowSeconds;
        }
        if (ttlSeconds == 0) {
            return 1L;
        }
        return ttlSeconds;
    }
}
