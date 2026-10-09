package com.motadev.clone_reddit.messaging.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox.publisher")
public record OutboxPublisherProperties(
        boolean enabled,
        int batchSize,
        long advisoryLockId,
        int maxAttempts
) {

    public OutboxPublisherProperties {
        if (batchSize < 1) {
            throw new IllegalArgumentException("app.outbox.publisher.batch-size must be >= 1");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("app.outbox.publisher.max-attempts must be >= 1");
        }
    }
}
