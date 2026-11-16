package com.motadev.clone_reddit.media.service.impl;

import com.motadev.clone_reddit.media.config.MediaOrphanSweepProperties;
import com.motadev.clone_reddit.media.logging.MediaEventLog;
import com.motadev.clone_reddit.media.repository.MediaRepository;
import com.motadev.clone_reddit.media.service.S3ObjectStore;
import com.motadev.clone_reddit.media.service.S3ObjectStore.ListedObject;
import com.motadev.clone_reddit.media.service.S3ObjectStore.ObjectPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class MediaOrphanSweepJob {

    private final MediaOrphanSweepProperties properties;
    private final S3ObjectStore s3ObjectStore;
    private final MediaRepository mediaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final MediaEventLog mediaEventLog;

    public MediaOrphanSweepJob(MediaOrphanSweepProperties properties,
                               S3ObjectStore s3ObjectStore,
                               MediaRepository mediaRepository,
                               JdbcTemplate jdbcTemplate,
                               MediaEventLog mediaEventLog) {
        this.properties = properties;
        this.s3ObjectStore = s3ObjectStore;
        this.mediaRepository = mediaRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.mediaEventLog = mediaEventLog;
    }

    @Scheduled(cron = "${app.purge.media-orphans.cron}", zone = "${app.purge.media-orphans.zone}")
    public void sweep() {
        if (!properties.enabled()) {
            return;
        }

        if (!acquireLock()) {
            mediaEventLog.orphanSweepLockNotAcquired(properties.advisoryLockId());
            return;
        }

        int deleted = 0;
        try {
            Instant cutoff = Instant.now().minus(properties.gracePeriod());
            String continuationToken = null;

            do {
                ObjectPage page = s3ObjectStore.listObjects(continuationToken, properties.batchSize());
                deleted += deleteOrphans(page.objects(), cutoff);
                continuationToken = page.nextContinuationToken();
            } while (continuationToken != null);

            if (deleted > 0) {
                mediaEventLog.orphanSweepSuccess(deleted, properties.gracePeriod());
            } else {
                mediaEventLog.orphanSweepNothing(properties.gracePeriod());
            }
        } finally {
            releaseLock();
        }
    }

    private int deleteOrphans(List<ListedObject> objects, Instant cutoff) {
        if (objects.isEmpty()) {
            return 0;
        }

        List<String> candidateKeys = objects.stream()
                .filter(object -> object.lastModified().isBefore(cutoff))
                .map(ListedObject::key)
                .toList();
        if (candidateKeys.isEmpty()) {
            return 0;
        }

        Set<String> knownKeys = mediaRepository.findExistingObjectKeys(candidateKeys);
        int deleted = 0;
        for (String objectKey : candidateKeys) {
            if (knownKeys.contains(objectKey)) {
                continue;
            }
            if (s3ObjectStore.deleteObjectQuietly(objectKey)) {
                mediaEventLog.orphanSweepDeleted(s3ObjectStore.bucketName(), objectKey);
                deleted++;
            }
        }
        return deleted;
    }

    private boolean acquireLock() {
        Boolean result = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(?)",
                Boolean.class,
                properties.advisoryLockId()
        );
        return Boolean.TRUE.equals(result);
    }

    private void releaseLock() {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_unlock(?)",
                Boolean.class,
                properties.advisoryLockId()
        );
    }
}
