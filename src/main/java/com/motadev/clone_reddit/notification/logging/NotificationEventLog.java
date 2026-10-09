package com.motadev.clone_reddit.notification.logging;

import com.motadev.clone_reddit.notification.service.impl.NotificationConsumer;
import com.motadev.clone_reddit.notification.service.impl.NotificationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotificationEventLog {

    private final Logger serviceLog = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private final Logger consumerLog = LoggerFactory.getLogger(NotificationConsumer.class);

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

    public void markedRead(UUID notificationId, UUID userId) {
        serviceLog.atInfo()
                .addKeyValue("event", "notification.read.success")
                .addKeyValue("notificationId", notificationId)
                .addKeyValue("userId", userId)
                .setMessage("Notification marked as read")
                .log();
    }
}
