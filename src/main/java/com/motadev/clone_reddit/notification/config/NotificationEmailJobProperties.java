package com.motadev.clone_reddit.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.email")
public record NotificationEmailJobProperties(
        boolean enabled,
        int batchSize,
        long advisoryLockId,
        int maxAttempts
) {

    public NotificationEmailJobProperties {
        if (batchSize < 1) {
            throw new IllegalArgumentException("app.notification.email.batch-size must be >= 1");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("app.notification.email.max-attempts must be >= 1");
        }
    }
}
