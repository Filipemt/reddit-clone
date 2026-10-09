package com.motadev.clone_reddit.messaging.logging;

import com.motadev.clone_reddit.messaging.config.RabbitMqTopologyConfig;
import com.motadev.clone_reddit.messaging.outbox.service.impl.OutboxPublisherJob;
import com.motadev.clone_reddit.messaging.outbox.service.impl.OutboxServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MessagingEventLog {

    private final Logger topologyLog = LoggerFactory.getLogger(RabbitMqTopologyConfig.class);
    private final Logger outboxLog = LoggerFactory.getLogger(OutboxServiceImpl.class);
    private final Logger publisherLog = LoggerFactory.getLogger(OutboxPublisherJob.class);

    public void topologyReady(String exchange, String notificationQueue, String notificationDlq) {
        topologyLog.atInfo()
                .addKeyValue("event", "messaging.topology.ready")
                .addKeyValue("exchange", exchange)
                .addKeyValue("notificationQueue", notificationQueue)
                .addKeyValue("notificationDlq", notificationDlq)
                .setMessage("RabbitMQ topology beans registered")
                .log();
    }

    public void outboxEnqueued(UUID eventId, String eventType, String routingKey, UUID aggregateId) {
        outboxLog.atDebug()
                .addKeyValue("event", "messaging.outbox.enqueued")
                .addKeyValue("eventId", eventId)
                .addKeyValue("eventType", eventType)
                .addKeyValue("routingKey", routingKey)
                .addKeyValue("aggregateId", aggregateId)
                .setMessage("Domain event stored in outbox")
                .log();
    }

    public void outboxPublished(UUID eventId, String eventType, String routingKey) {
        publisherLog.atInfo()
                .addKeyValue("event", "messaging.outbox.published")
                .addKeyValue("eventId", eventId)
                .addKeyValue("eventType", eventType)
                .addKeyValue("routingKey", routingKey)
                .setMessage("Outbox event published to RabbitMQ")
                .log();
    }

    public void outboxPublishFailedRetry(UUID eventId, String eventType, int attempts, Throwable cause) {
        publisherLog.atWarn()
                .addKeyValue("event", "messaging.outbox.publish_failed_retry")
                .addKeyValue("eventId", eventId)
                .addKeyValue("eventType", eventType)
                .addKeyValue("attempts", attempts)
                .setMessage("Outbox publish failed; will retry")
                .setCause(cause)
                .log();
    }

    public void outboxPublishFailedPermanent(UUID eventId, String eventType, int attempts, Throwable cause) {
        publisherLog.atError()
                .addKeyValue("event", "messaging.outbox.publish_failed")
                .addKeyValue("eventId", eventId)
                .addKeyValue("eventType", eventType)
                .addKeyValue("attempts", attempts)
                .setMessage("Outbox publish exhausted retries; marked FAILED")
                .setCause(cause)
                .log();
    }

    public void outboxPublisherLockNotAcquired(long advisoryLockId) {
        publisherLog.atDebug()
                .addKeyValue("event", "messaging.outbox.lock_not_acquired")
                .addKeyValue("advisoryLockId", advisoryLockId)
                .setMessage("Outbox publisher skipped; advisory lock held by another instance")
                .log();
    }

    public void outboxPublisherBatch(int published, int failed) {
        publisherLog.atInfo()
                .addKeyValue("event", "messaging.outbox.batch")
                .addKeyValue("published", published)
                .addKeyValue("failed", failed)
                .setMessage("Outbox publisher batch completed")
                .log();
    }
}
