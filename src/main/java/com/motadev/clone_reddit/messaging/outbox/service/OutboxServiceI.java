package com.motadev.clone_reddit.messaging.outbox.service;

import java.util.UUID;

public interface OutboxServiceI {

    UUID enqueue(String aggregateType, UUID aggregateId, String eventType, String routingKey, Object payload);
}
