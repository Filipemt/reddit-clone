package com.motadev.clone_reddit.messaging.outbox.service.impl;

import com.motadev.clone_reddit.messaging.logging.MessagingEventLog;
import com.motadev.clone_reddit.messaging.outbox.entity.OutboxEvent;
import com.motadev.clone_reddit.messaging.outbox.entity.OutboxEventStatus;
import com.motadev.clone_reddit.messaging.outbox.repository.OutboxEventRepository;
import com.motadev.clone_reddit.messaging.outbox.service.OutboxServiceI;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
public class OutboxServiceImpl implements OutboxServiceI {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final MessagingEventLog messagingEventLog;

    public OutboxServiceImpl(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            MessagingEventLog messagingEventLog
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.messagingEventLog = messagingEventLog;
    }

    @Override
    @Transactional
    public UUID enqueue(String aggregateType, UUID aggregateId, String eventType, String routingKey, Object payload) {
        var event = new OutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setRoutingKey(routingKey);
        event.setPayload(serialize(payload));
        event.setStatus(OutboxEventStatus.PENDING);
        event.setAttempts(0);

        outboxEventRepository.save(event);
        messagingEventLog.outboxEnqueued(event.getEventId(), eventType, routingKey, aggregateId);
        return event.getEventId();
    }

    private String serialize(Object payload) {
        if (payload == null) {
            return "{}";
        }
        if (payload instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Failed to serialize outbox payload for event", ex);
        }
    }
}
