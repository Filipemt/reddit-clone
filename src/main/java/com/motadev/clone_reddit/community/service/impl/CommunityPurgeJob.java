package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.community.config.CommunityPurgeProperties;
import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.media.service.MediaServiceI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
public class CommunityPurgeJob {

    private final CommunityPurgeProperties properties;
    private final CommunityRepository communityRepository;
    private final MediaServiceI mediaServiceI;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public CommunityPurgeJob(CommunityPurgeProperties properties,
                             CommunityRepository communityRepository,
                             MediaServiceI mediaServiceI,
                             JdbcTemplate jdbcTemplate,
                             TransactionTemplate transactionTemplate) {
        this.properties = properties;
        this.communityRepository = communityRepository;
        this.mediaServiceI = mediaServiceI;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "60000")
    public void purge() {
        if (!properties.enabled()) {
            return;
        }

        if (!acquireLock()) {
            log.atDebug()
                    .addKeyValue("event", "community.purge.lock_not_acquired")
                    .addKeyValue("advisoryLockId", properties.advisoryLockId())
                    .setMessage("Another instance holds the purge lock; skipping run")
                    .log();
            return;
        }

        int purged = 0;
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.retentionDays());
            while (true) {
                List<Community> candidates = communityRepository.findCandidatesForPurge(cutoff,
                        org.springframework.data.domain.PageRequest.of(0, properties.batchSize()));
                if (candidates.isEmpty()) {
                    break;
                }

                transactionTemplate.execute(status -> {
                    for (Community community : candidates) {
                        mediaServiceI.deleteAfterCommit(mediaIdsOf(community));
                        communityRepository.delete(community);
                    }
                    return null;
                });

                purged += candidates.size();
                if (candidates.size() < properties.batchSize()) {
                    break;
                }
            }

            if (purged > 0) {
                log.atInfo()                        .addKeyValue("event", "community.purge.success")
                        .addKeyValue("purgedCount", purged)
                        .addKeyValue("retentionDays", properties.retentionDays())
                        .setMessage("Purged communities past retention window")
                        .log();
            } else {
                log.atDebug()                        .addKeyValue("event", "community.purge.nothing")
                        .addKeyValue("retentionDays", properties.retentionDays())
                        .setMessage("No communities eligible for purge")
                        .log();
            }
        } finally {
            releaseLock();
        }
    }

    private boolean acquireLock() {
        Long result = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(?)",
                Long.class,
                properties.advisoryLockId()
        );
        return result != null && result == 1L;
    }

    private void releaseLock() {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_unlock(?)",
                Long.class,
                properties.advisoryLockId()
        );
    }

    private Collection<UUID> mediaIdsOf(Community community) {
        return Stream.of(community.getIconMediaId(), community.getBannerMediaId())
                .filter(Objects::nonNull)
                .toList();
    }
}