package com.motadev.clone_reddit.media.logging;

import com.motadev.clone_reddit.media.service.impl.MediaOrphanSweepJob;
import com.motadev.clone_reddit.media.service.impl.S3ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class MediaEventLog {

    private final Logger log = LoggerFactory.getLogger(S3ServiceImpl.class);
    private final Logger orphanSweepLog = LoggerFactory.getLogger(MediaOrphanSweepJob.class);

    public MediaEventLog() {
    }

    public void uploadNoActiveTransaction(UUID mediaId, String objectKey) {
        log.atDebug()
                .addKeyValue("event", "media.upload.no_active_transaction")
                .addKeyValue("mediaId", mediaId)
                .addKeyValue("objectKey", objectKey)
                .setMessage("Upload performed outside a transaction; S3 rollback is the caller's responsibility.")
                .log();
    }

    public void uploadRollbackUnknown(UUID mediaId, String objectKey) {
        log.atWarn()
                .addKeyValue("event", "media.upload.rollback_unknown")
                .addKeyValue("mediaId", mediaId)
                .addKeyValue("objectKey", objectKey)
                .setMessage("Transaction outcome is unknown; the S3 object was kept to avoid breaking a reference that may have been committed.")
                .log();
    }

    public void uploadRollbackFailed(String bucket, String objectKey, RuntimeException ex) {
        log.atError()
                .setCause(ex)
                .addKeyValue("event", "media.upload.rollback_failed")
                .addKeyValue("bucket", bucket)
                .addKeyValue("objectKey", objectKey)
                .setMessage("Failed to delete the S3 object during upload rollback; the object may be orphaned.")
                .log();
    }

    public void deleteNoActiveTransaction(int mediaCount) {
        log.atDebug()
                .addKeyValue("event", "media.delete.no_active_transaction")
                .addKeyValue("mediaCount", mediaCount)
                .setMessage("Media deletion performed outside a transaction; the S3 objects are deleted right away.")
                .log();
    }

    public void orphanSweepLockNotAcquired(long advisoryLockId) {
        orphanSweepLog.atDebug()
                .addKeyValue("event", "media.orphan_sweep.lock_not_acquired")
                .addKeyValue("advisoryLockId", advisoryLockId)
                .setMessage("Another instance holds the media orphan sweep lock; skipping run")
                .log();
    }

    public void orphanSweepSuccess(int deletedCount, Duration gracePeriod) {
        orphanSweepLog.atInfo()
                .addKeyValue("event", "media.orphan_sweep.success")
                .addKeyValue("deletedCount", deletedCount)
                .addKeyValue("gracePeriod", gracePeriod.toString())
                .setMessage("Deleted orphaned S3 objects past the grace period")
                .log();
    }

    public void orphanSweepNothing(Duration gracePeriod) {
        orphanSweepLog.atDebug()
                .addKeyValue("event", "media.orphan_sweep.nothing")
                .addKeyValue("gracePeriod", gracePeriod.toString())
                .setMessage("No orphaned S3 objects eligible for deletion")
                .log();
    }

    public void orphanSweepDeleted(String bucket, String objectKey) {
        orphanSweepLog.atInfo()
                .addKeyValue("event", "media.orphan_sweep.deleted")
                .addKeyValue("bucket", bucket)
                .addKeyValue("objectKey", objectKey)
                .setMessage("Deleted orphaned S3 object")
                .log();
    }

    public void orphanSweepDeleteFailed(String bucket, String objectKey, RuntimeException ex) {
        orphanSweepLog.atError()
                .setCause(ex)
                .addKeyValue("event", "media.orphan_sweep.delete_failed")
                .addKeyValue("bucket", bucket)
                .addKeyValue("objectKey", objectKey)
                .setMessage("Failed to delete an orphaned S3 object during sweep")
                .log();
    }
}
