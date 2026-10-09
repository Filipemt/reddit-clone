package com.motadev.clone_reddit.notification.service.impl;

import com.motadev.clone_reddit.messaging.outbox.dto.OutboxMessageEnvelope;
import com.motadev.clone_reddit.notification.dtos.response.NotificationResponseDTO;
import com.motadev.clone_reddit.notification.entity.Notification;
import com.motadev.clone_reddit.notification.logging.NotificationEventLog;
import com.motadev.clone_reddit.notification.repository.NotificationRepository;
import com.motadev.clone_reddit.notification.service.NotificationServiceI;
import com.motadev.clone_reddit.shared.dtos.response.PagedResponseDTO;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationServiceI {

    private static final int FALLBACK_PAGE_SIZE = 20;

    private final NotificationRepository notificationRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final ObjectMapper objectMapper;
    private final NotificationEventLog notificationEventLog;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            AuthenticatedUserProvider authenticatedUserProvider,
            ObjectMapper objectMapper,
            NotificationEventLog notificationEventLog
    ) {
        this.notificationRepository = notificationRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
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

    @Override
    @Transactional
    public PagedResponseDTO<NotificationResponseDTO> listMine(Pageable pageable) {
        UUID userId = authenticatedUserProvider.extractUserIdFromAuthentication();
        Page<Notification> page = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(
                userId,
                withoutClientSort(pageable)
        );
        return PagedResponseDTO.from(page, this::toDto);
    }

    @Override
    @Transactional
    public void markRead(UUID notificationId) {
        UUID userId = authenticatedUserProvider.extractUserIdFromAuthentication();
        Notification notification = notificationRepository.findByNotificationIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationEventLog.markedRead(notificationId, userId);
        }
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

    private NotificationResponseDTO toDto(Notification notification) {
        return new NotificationResponseDTO(
                notification.getNotificationId(),
                notification.getType(),
                notification.getPayload(),
                notification.getReadAt() != null,
                notification.getCreatedAt()
        );
    }

    private static Pageable withoutClientSort(Pageable pageable) {
        if (!pageable.isPaged()) {
            return PageRequest.of(0, FALLBACK_PAGE_SIZE);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }
}
