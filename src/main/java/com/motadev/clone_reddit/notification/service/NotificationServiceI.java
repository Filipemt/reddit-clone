package com.motadev.clone_reddit.notification.service;

import com.motadev.clone_reddit.messaging.outbox.dto.OutboxMessageEnvelope;
import com.motadev.clone_reddit.notification.dtos.response.NotificationResponseDTO;
import com.motadev.clone_reddit.shared.dtos.response.PagedResponseDTO;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationServiceI {

    void ingest(OutboxMessageEnvelope envelope);

    PagedResponseDTO<NotificationResponseDTO> listMine(Pageable pageable);

    void markRead(UUID notificationId);
}
