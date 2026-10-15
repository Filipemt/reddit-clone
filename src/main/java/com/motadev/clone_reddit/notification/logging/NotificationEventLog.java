package com.motadev.clone_reddit.notification.logging;

import com.motadev.clone_reddit.notification.service.impl.NotificationConsumer;
import com.motadev.clone_reddit.notification.service.impl.NotificationEmailJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotificationEventLog {

    private final Logger consumerLog = LoggerFactory.getLogger(NotificationConsumer.class);
    private final Logger emailJobLog = LoggerFactory.getLogger(NotificationEmailJob.class);

    public void created(UUID notificationId, UUID recipientId, String type, UUID eventId) {
        consumerLog.atInfo()
                .addKeyValue("event", "notification.create.success")
                .addKeyValue("notificationId", notificationId)
                .addKeyValue("recipientId", recipientId)
                .addKeyValue("type", type)
                .addKeyValue("eventId", eventId)
                .setMessage("Notification persisted from queue event")
                .log();
    }

    public void duplicateSkipped(UUID eventId) {
        consumerLog.atDebug()
                .addKeyValue("event", "notification.create.duplicate")
                .addKeyValue("eventId", eventId)
                .setMessage("Duplicate outbox event ignored")
                .log();
    }

    public void consumeFailed(UUID eventId, String type, Throwable cause) {
        consumerLog.atError()
                .addKeyValue("event", "notification.consume.failed")
                .addKeyValue("eventId", eventId)
                .addKeyValue("type", type)
                .setMessage("Failed to consume notification event")
                .setCause(cause)
                .log();
    }

    public void emailSent(UUID notificationId, UUID recipientId, String type) {
        emailJobLog.atInfo()
                .addKeyValue("event", "notification.email.sent")
                .addKeyValue("notificationId", notificationId)
                .addKeyValue("recipientId", recipientId)
                .addKeyValue("type", type)
                .setMessage("Notification email sent")
                .log();
    }

    public void emailRetry(UUID notificationId, UUID recipientId, String type, int attempts, Throwable cause) {
        emailJobLog.atWarn()
                .addKeyValue("event", "notification.email.retry")
                .addKeyValue("notificationId", notificationId)
                .addKeyValue("recipientId", recipientId)
                .addKeyValue("type", type)
                .addKeyValue("attempts", attempts)
                .setMessage("Notification email send failed; will retry")
                .setCause(cause)
                .log();
    }

    public void emailFailed(
            UUID notificationId,
            UUID recipientId,
            String type,
            int attempts,
            Throwable cause
    ) {
        emailJobLog.atError()
                .addKeyValue("event", "notification.email.failed")
                .addKeyValue("notificationId", notificationId)
                .addKeyValue("recipientId", recipientId)
                .addKeyValue("type", type)
                .addKeyValue("attempts", attempts)
                .setMessage("Notification email send exhausted retries; marked FAILED")
                .setCause(cause)
                .log();
    }

    public void emailRecipientInvalid(UUID notificationId, UUID recipientId, String type, String reason) {
        emailJobLog.atWarn()
                .addKeyValue("event", "notification.email.recipient_invalid")
                .addKeyValue("notificationId", notificationId)
                .addKeyValue("recipientId", recipientId)
                .addKeyValue("type", type)
                .addKeyValue("reason", reason)
                .setMessage("Notification email skipped; recipient invalid; marked FAILED")
                .log();
    }

    public void emailLockNotAcquired(long advisoryLockId) {
        emailJobLog.atDebug()
                .addKeyValue("event", "notification.email.lock_not_acquired")
                .addKeyValue("advisoryLockId", advisoryLockId)
                .setMessage("Notification email job skipped; advisory lock held by another instance")
                .log();
    }

    public void emailBatch(int sent, int failed) {
        emailJobLog.atInfo()
                .addKeyValue("event", "notification.email.batch")
                .addKeyValue("sent", sent)
                .addKeyValue("failed", failed)
                .setMessage("Notification email job batch completed")
                .log();
    }
}
