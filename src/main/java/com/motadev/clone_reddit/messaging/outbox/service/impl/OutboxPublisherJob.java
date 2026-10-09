package com.motadev.clone_reddit.messaging.outbox.service.impl;

import com.motadev.clone_reddit.messaging.config.RabbitMqProperties;
import com.motadev.clone_reddit.messaging.logging.MessagingEventLog;
import com.motadev.clone_reddit.messaging.outbox.config.OutboxPublisherProperties;
import com.motadev.clone_reddit.messaging.outbox.dto.OutboxMessageEnvelope;
import com.motadev.clone_reddit.messaging.outbox.entity.OutboxEvent;
import com.motadev.clone_reddit.messaging.outbox.entity.OutboxEventStatus;
import com.motadev.clone_reddit.messaging.outbox.repository.OutboxEventRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class OutboxPublisherJob {

    private final OutboxPublisherProperties properties;
    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties rabbitMqProperties;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MessagingEventLog messagingEventLog;

    public OutboxPublisherJob(
            OutboxPublisherProperties properties,
            OutboxEventRepository outboxEventRepository,
            RabbitTemplate rabbitTemplate,
            RabbitMqProperties rabbitMqProperties,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            MessagingEventLog messagingEventLog
    ) {
        this.properties = properties;
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMqProperties = rabbitMqProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.messagingEventLog = messagingEventLog;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.fixed-delay-ms}", initialDelayString = "${app.outbox.publisher.initial-delay-ms}")
    public void publishPending() {
        if (!properties.enabled()) {
            return;
        }

        if (!acquireLock()) {
            messagingEventLog.outboxPublisherLockNotAcquired(properties.advisoryLockId());
            return;
        }

        int published = 0;
        int failed = 0;
        try {
            while (true) {
                List<OutboxEvent> batch = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                        OutboxEventStatus.PENDING,
                        PageRequest.of(0, properties.batchSize())
                );
                if (batch.isEmpty()) {
                    break;
                }

                for (OutboxEvent event : batch) {
                    boolean ok = Boolean.TRUE.equals(transactionTemplate.execute(status -> publishOne(event)));
                    if (ok) {
                        published++;
                    } else {
                        failed++;
                    }
                }

                if (batch.size() < properties.batchSize()) {
                    break;
                }
            }

            if (published > 0 || failed > 0) {
                messagingEventLog.outboxPublisherBatch(published, failed);
            }
        } finally {
            releaseLock();
        }
    }

    private boolean publishOne(OutboxEvent event) {
        try {
            var envelope = new OutboxMessageEnvelope(
                    event.getEventId(),
                    event.getEventType(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getCreatedAt() != null
                            ? event.getCreatedAt().toInstant(ZoneOffset.UTC)
                            : Instant.now(),
                    event.getPayload()
            );

            rabbitTemplate.convertAndSend(
                    rabbitMqProperties.exchange(),
                    event.getRoutingKey(),
                    envelope
            );

            event.setStatus(OutboxEventStatus.SENT);
            event.setPublishedAt(LocalDateTime.now());
            event.setAttempts(event.getAttempts() + 1);
            event.setLastError(null);
            outboxEventRepository.save(event);
            messagingEventLog.outboxPublished(event.getEventId(), event.getEventType(), event.getRoutingKey());
            return true;
        } catch (RuntimeException ex) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setLastError(truncate(ex.getMessage()));
            if (attempts >= properties.maxAttempts()) {
                event.setStatus(OutboxEventStatus.FAILED);
                messagingEventLog.outboxPublishFailedPermanent(
                        event.getEventId(),
                        event.getEventType(),
                        attempts,
                        ex
                );
            } else {
                messagingEventLog.outboxPublishFailedRetry(
                        event.getEventId(),
                        event.getEventType(),
                        attempts,
                        ex
                );
            }
            outboxEventRepository.save(event);
            return false;
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

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
