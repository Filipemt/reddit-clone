package com.motadev.clone_reddit.community.service.impl;

import com.motadev.clone_reddit.community.config.CommunityPurgeProperties;
import com.motadev.clone_reddit.community.entity.Community;
import com.motadev.clone_reddit.community.logging.CommunityEventLog;
import com.motadev.clone_reddit.community.repository.CommunityRepository;
import com.motadev.clone_reddit.media.service.MediaServiceI;
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

@Service
public class CommunityPurgeJob {

    private final CommunityPurgeProperties properties;
    private final CommunityRepository communityRepository;
    private final MediaServiceI mediaServiceI;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final CommunityEventLog communityEventLog;

    public CommunityPurgeJob(CommunityPurgeProperties properties,
                             CommunityRepository communityRepository,
                             MediaServiceI mediaServiceI,
                             JdbcTemplate jdbcTemplate,
                             TransactionTemplate transactionTemplate,
                             CommunityEventLog communityEventLog) {
        this.properties = properties;
        this.communityRepository = communityRepository;
        this.mediaServiceI = mediaServiceI;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.communityEventLog = communityEventLog;
    }

    @Scheduled(fixedDelayString = "60000")
    public void purge() {
        if (!properties.enabled()) {
            return;
        }

        if (!acquireLock()) {
            communityEventLog.purgeLockNotAcquired(properties.advisoryLockId());
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
                        Collection<UUID> mediaIds = mediaIdsOf(community);
                        communityRepository.delete(community);
                        mediaServiceI.deleteAfterCommit(mediaIds);
                    }
                    return null;
                });

                purged += candidates.size();
                if (candidates.size() < properties.batchSize()) {
                    break;
                }
            }

            if (purged > 0) {
                communityEventLog.purgeSuccess(purged, properties.retentionDays());
            } else {
                communityEventLog.purgeNothing(properties.retentionDays());
            }
        } finally {
            releaseLock();
        }
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

    private Collection<UUID> mediaIdsOf(Community community) {
        return Stream.of(community.getIconMediaId(), community.getBannerMediaId())
                .filter(Objects::nonNull)
                .toList();
    }
}
