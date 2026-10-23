package com.motadev.clone_reddit.shared.ratelimit.logging;

import com.motadev.clone_reddit.shared.ratelimit.config.RateLimitKeyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RateLimitEventLog {

    private final Logger log = LoggerFactory.getLogger(RateLimitEventLog.class);

    public void exceeded(String policy, RateLimitKeyType keyType, String path) {
        log.atWarn()
                .addKeyValue("event", "ratelimit.exceeded")
                .addKeyValue("policy", policy)
                .addKeyValue("keyType", keyType.name())
                .addKeyValue("path", path)
                .setMessage("Rate limit exceeded")
                .log();
    }

    public void storeFailed(String policy, String path, Exception cause) {
        log.atError()
                .setCause(cause)
                .addKeyValue("event", "ratelimit.store.failed")
                .addKeyValue("policy", policy)
                .addKeyValue("path", path)
                .setMessage("Rate limit store failed; allowing request")
                .log();
    }
}
