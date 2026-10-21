package com.motadev.clone_reddit.shared.ratelimit.store;

public interface RateLimitStore {

    RateLimitDecision consume(String bucketKey, int limit, int windowSeconds);
}
