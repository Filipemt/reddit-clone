package com.motadev.clone_reddit.notification.service;

import com.motadev.clone_reddit.messaging.outbox.dto.OutboxMessageEnvelope;

public interface NotificationServiceI {

    void ingest(OutboxMessageEnvelope envelope);
}
