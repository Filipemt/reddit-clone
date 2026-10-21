package com.motadev.clone_reddit.shared.exception;

public class RateLimitExceededException extends RuntimeException {

    private final int limit;
    private final int remaining;
    private final long resetEpochSeconds;
    private final long retryAfterSeconds;
    private final String policy;

    public RateLimitExceededException(
            String policy,
            int limit,
            int remaining,
            long resetEpochSeconds,
            long retryAfterSeconds
    ) {
        super("Too many requests. Try again later.");
        this.policy = policy;
        this.limit = limit;
        this.remaining = remaining;
        this.resetEpochSeconds = resetEpochSeconds;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getPolicy() {
        return policy;
    }

    public int getLimit() {
        return limit;
    }

    public int getRemaining() {
        return remaining;
    }

    public long getResetEpochSeconds() {
        return resetEpochSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
