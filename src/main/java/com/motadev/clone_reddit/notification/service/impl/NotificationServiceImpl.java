package com.motadev.clone_reddit.notification.service.impl;

import com.motadev.clone_reddit.messaging.outbox.dto.OutboxMessageEnvelope;
import com.motadev.clone_reddit.notification.entity.Notification;
import com.motadev.clone_reddit.notification.logging.NotificationEventLog;
import com.motadev.clone_reddit.notification.repository.NotificationRepository;
import com.motadev.clone_reddit.notification.service.NotificationServiceI;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationServiceI {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final NotificationEventLog notificationEventLog;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            ObjectMapper objectMapper,
            NotificationEventLog notificationEventLog
    ) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
        this.notificationEventLog = notificationEventLog;
    }

    @Override
    @Transactional
    public void ingest(OutboxMessageEnvelope envelope) {
        if (notificationRepository.existsByEventId(envelope.eventId())) {
            notificationEventLog.duplicateSkipped(envelope.eventId());
            return;
        }

        UUID recipientId = resolveRecipientId(envelope);
        if (recipientId == null) {
            return;
        }

        Notification notification = new Notification();
        notification.setNotificationId(UUID.randomUUID());
        notification.setEventId(envelope.eventId());
        notification.setRecipientId(recipientId);
        notification.setType(envelope.eventType());
        notification.setPayload(envelope.payload() == null ? "{}" : envelope.payload());
        notificationRepository.save(notification);

        notificationEventLog.created(
                notification.getNotificationId(),
                recipientId,
                envelope.eventType(),
                envelope.eventId()
        );
    }

    private UUID resolveRecipientId(OutboxMessageEnvelope envelope) {
        try {
            JsonNode node = objectMapper.readTree(envelope.payload() == null ? "{}" : envelope.payload());
            if (node.hasNonNull("recipientId") && !node.get("recipientId").asString().isBlank()) {
                return UUID.fromString(node.get("recipientId").asString());
            }
            if (node.hasNonNull("ownerId") && !node.get("ownerId").asString().isBlank()) {
                return UUID.fromString(node.get("ownerId").asString());
            }
            return null;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid notification payload", ex);
        }
    }
}
