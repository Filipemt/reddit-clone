package com.motadev.clone_reddit.community.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.purge.communities")
public record CommunityPurgeProperties(
        int retentionDays,
        int batchSize,
        boolean enabled,
        long advisoryLockId
) {

    public CommunityPurgeProperties {
        if (retentionDays < 0) {
            throw new IllegalArgumentException("app.purge.communities.retention-days must be >= 0");
        }

        if (batchSize < 1) {
            throw new IllegalArgumentException("app.purge.communities.batch-size must be >= 1");
        }
    }
}