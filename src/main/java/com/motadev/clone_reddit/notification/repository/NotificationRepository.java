package com.motadev.clone_reddit.notification.repository;

import com.motadev.clone_reddit.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByEventId(UUID eventId);

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    Optional<Notification> findByNotificationIdAndRecipientId(UUID notificationId, UUID recipientId);
}
