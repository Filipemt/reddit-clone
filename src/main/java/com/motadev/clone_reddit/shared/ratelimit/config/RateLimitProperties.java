package com.motadev.clone_reddit.shared.ratelimit.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        @NotNull Map<String, @Valid RateLimitPolicyProperties> policies,
        @NotNull List<@Valid RateLimitRuleProperties> rules
) {

    public RateLimitProperties {
        policies = policies == null ? new HashMap<>() : policies;
        rules = rules == null ? new ArrayList<>() : List.copyOf(rules);
    }
}
