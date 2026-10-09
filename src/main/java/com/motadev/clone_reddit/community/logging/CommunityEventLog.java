package com.motadev.clone_reddit.community.logging;

import com.motadev.clone_reddit.community.service.impl.CommunityPurgeJob;
import com.motadev.clone_reddit.community.service.impl.CommunityServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CommunityEventLog {

    private final Logger communityLog = LoggerFactory.getLogger(CommunityServiceImpl.class);
    private final Logger purgeLog = LoggerFactory.getLogger(CommunityPurgeJob.class);

    public CommunityEventLog() {
    }

    public void deleteAlreadyDeleted(UUID communityId, UUID userId) {
        communityLog.atDebug()
                .addKeyValue("event", "community.delete.already_deleted")
                .addKeyValue("communityId", communityId)
                .addKeyValue("userId", userId)
                .setMessage("Community was already soft deleted; nothing to do")
                .log();
    }

    public void deleteSuccess(UUID communityId, UUID userId, boolean isOwner, int mediaCount) {
        communityLog.atInfo()
                .addKeyValue("event", "community.delete.success")
                .addKeyValue("communityId", communityId)
                .addKeyValue("userId", userId)
                .addKeyValue("isOwner", isOwner)
                .addKeyValue("mediaCount", mediaCount)
                .setMessage("Community soft deleted; media retained until purge")
                .log();
    }

    public void mediaForbidden(UUID communityId, UUID userId) {
        communityLog.atWarn()
                .addKeyValue("event", "community.media.forbidden")
                .addKeyValue("communityId", communityId)
                .addKeyValue("userId", userId)
                .setMessage("Attempt to change the media of a community the user does not own")
                .log();
    }

    public void createConflict(String name, String slug, boolean deleted) {
        communityLog.atWarn()
                .addKeyValue("event", "community.create.conflict")
                .addKeyValue("name", name)
                .addKeyValue("slug", slug)
                .addKeyValue("deleted", deleted)
                .setMessage("Attempt to create a community with an existing name or slug")
                .log();
    }

    public void purgeLockNotAcquired(long advisoryLockId) {
        purgeLog.atDebug()
                .addKeyValue("event", "community.purge.lock_not_acquired")
                .addKeyValue("advisoryLockId", advisoryLockId)
                .setMessage("Another instance holds the purge lock; skipping run")
                .log();
    }

    public void purgeSuccess(int purgedCount, int retentionDays) {
        purgeLog.atInfo()
                .addKeyValue("event", "community.purge.success")
                .addKeyValue("purgedCount", purgedCount)
                .addKeyValue("retentionDays", retentionDays)
                .setMessage("Purged communities past retention window")
                .log();
    }

    public void purgeNothing(int retentionDays) {
        purgeLog.atDebug()
                .addKeyValue("event", "community.purge.nothing")
                .addKeyValue("retentionDays", retentionDays)
                .setMessage("No communities eligible for purge")
                .log();
    }
}
