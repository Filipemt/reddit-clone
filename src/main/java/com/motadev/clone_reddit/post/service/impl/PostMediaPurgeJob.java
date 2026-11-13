package com.motadev.clone_reddit.post.service.impl;

import com.motadev.clone_reddit.media.service.MediaServiceI;
import com.motadev.clone_reddit.post.config.PostMediaPurgeProperties;
import com.motadev.clone_reddit.post.entity.Post;
import com.motadev.clone_reddit.post.logging.PostEventLog;
import com.motadev.clone_reddit.post.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PostMediaPurgeJob {

    private final PostMediaPurgeProperties properties;
    private final PostRepository postRepository;
    private final MediaServiceI mediaServiceI;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final PostEventLog postEventLog;

    public PostMediaPurgeJob(PostMediaPurgeProperties properties,
                             PostRepository postRepository,
                             MediaServiceI mediaServiceI,
                             JdbcTemplate jdbcTemplate,
                             TransactionTemplate transactionTemplate,
                             PostEventLog postEventLog) {
        this.properties = properties;
        this.postRepository = postRepository;
        this.mediaServiceI = mediaServiceI;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.postEventLog = postEventLog;
    }

    @Scheduled(cron = "${app.purge.posts.cron}", zone = "${app.purge.posts.zone}")
    public void purge() {
        if (!properties.enabled()) {
            return;
        }

        if (!acquireLock()) {
            postEventLog.mediaPurgeLockNotAcquired(properties.advisoryLockId());
            return;
        }

        int purged = 0;
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.retentionDays());
            while (true) {
                List<Post> candidates = postRepository.findCandidatesForMediaPurge(cutoff,
                        PageRequest.of(0, properties.batchSize()));
                if (candidates.isEmpty()) {
                    break;
                }

                transactionTemplate.execute(status -> {
                    for (Post post : candidates) {
                        UUID mediaId = post.getMediaId();
                        post.setMediaId(null);
                        postRepository.save(post);
                        mediaServiceI.deleteAfterCommit(List.of(mediaId));
                    }
                    return null;
                });

                purged += candidates.size();
                if (candidates.size() < properties.batchSize()) {
                    break;
                }
            }

            if (purged > 0) {
                postEventLog.mediaPurgeSuccess(purged, properties.retentionDays());
            } else {
                postEventLog.mediaPurgeNothing(properties.retentionDays());
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
}
