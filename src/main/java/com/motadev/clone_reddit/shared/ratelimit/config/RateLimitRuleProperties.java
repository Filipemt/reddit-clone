package com.motadev.clone_reddit.shared.ratelimit.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RateLimitRuleProperties(
        @NotBlank String method,
        @NotBlank String pathPattern,
        @NotBlank String policy,
        @NotNull RateLimitKeyType key
) {
}
