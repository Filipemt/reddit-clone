package com.motadev.clone_reddit.notification.service.impl;

import com.motadev.clone_reddit.messaging.outbox.dto.OutboxMessageEnvelope;
import com.motadev.clone_reddit.notification.logging.NotificationEventLog;
import com.motadev.clone_reddit.notification.service.NotificationServiceI;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private final NotificationServiceI notificationServiceI;
    private final NotificationEventLog notificationEventLog;

    public NotificationConsumer(
            NotificationServiceI notificationServiceI,
            NotificationEventLog notificationEventLog
    ) {
        this.notificationServiceI = notificationServiceI;
        this.notificationEventLog = notificationEventLog;
    }

    @RabbitListener(queues = "${app.rabbitmq.notification-queue}")
    public void onMessage(OutboxMessageEnvelope envelope) {
        try {
            notificationServiceI.ingest(envelope);
        } catch (RuntimeException ex) {
            notificationEventLog.consumeFailed(
                    envelope == null ? null : envelope.eventId(),
                    envelope == null ? null : envelope.eventType(),
                    ex
            );
            throw ex;
        }
    }
}
