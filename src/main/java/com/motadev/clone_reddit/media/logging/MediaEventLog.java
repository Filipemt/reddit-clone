package com.motadev.clone_reddit.media.logging;

import com.motadev.clone_reddit.media.service.impl.S3ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MediaEventLog {

    private final Logger log = LoggerFactory.getLogger(S3ServiceImpl.class);

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
}
