package com.motadev.clone_reddit.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.purge.media-orphans")
public record MediaOrphanSweepProperties(
        Duration gracePeriod,
        int batchSize,
        boolean enabled,
        long advisoryLockId
) {

    public MediaOrphanSweepProperties {
        if (gracePeriod == null || gracePeriod.isNegative()) {
            throw new IllegalArgumentException("app.purge.media-orphans.grace-period must be >= 0");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("app.purge.media-orphans.batch-size must be >= 1");
        }
    }
}
