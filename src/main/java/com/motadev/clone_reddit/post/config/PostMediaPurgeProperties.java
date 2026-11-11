package com.motadev.clone_reddit.post.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.purge.posts")
public record PostMediaPurgeProperties(
        int retentionDays,
        int batchSize,
        boolean enabled,
        long advisoryLockId
) {

    public PostMediaPurgeProperties {
        if (retentionDays < 0) {
            throw new IllegalArgumentException("app.purge.posts.retention-days must be >= 0");
        }

        if (batchSize < 1) {
            throw new IllegalArgumentException("app.purge.posts.batch-size must be >= 1");
        }
    }
}
