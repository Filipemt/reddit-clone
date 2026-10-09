package com.motadev.clone_reddit.notification.dtos.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponseDTO(
        UUID notificationId,
        String type,
        String payload,
        boolean read,
        LocalDateTime createdAt
) {
}
