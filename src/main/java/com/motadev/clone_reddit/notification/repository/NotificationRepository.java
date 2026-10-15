package com.motadev.clone_reddit.notification.repository;

import com.motadev.clone_reddit.notification.entity.Notification;
import com.motadev.clone_reddit.notification.entity.NotificationDeliveryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByEventId(UUID eventId);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.deliveryStatus = :status
            ORDER BY n.createdAt ASC
            """)
    List<Notification> findByDeliveryStatusOrderByCreatedAtAsc(
            @Param("status") NotificationDeliveryStatus status,
            Pageable pageable
    );
}
