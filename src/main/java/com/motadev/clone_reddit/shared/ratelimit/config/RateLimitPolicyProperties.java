package com.motadev.clone_reddit.shared.ratelimit.config;

import jakarta.validation.constraints.Min;

public record RateLimitPolicyProperties(
        @Min(1) int limit,
        @Min(1) int windowSeconds
) {
}
