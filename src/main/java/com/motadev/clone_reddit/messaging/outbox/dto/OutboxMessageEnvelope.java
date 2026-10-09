package com.motadev.clone_reddit.messaging.outbox.dto;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessageEnvelope(
        UUID eventId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        String payload
) {
}
