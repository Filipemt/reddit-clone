package com.motadev.clone_reddit.shared.ratelimit.store;

public record RateLimitDecision(
        boolean allowed,
        int limit,
        int remaining,
        long resetEpochSeconds,
        long retryAfterSeconds
) {

    public static RateLimitDecision allowPassthrough(int limit) {
        long now = System.currentTimeMillis() / 1000L;
        return new RateLimitDecision(true, limit, limit, now, 0L);
    }
}
