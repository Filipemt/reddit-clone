package com.motadev.clone_reddit.messaging.outbox.repository;

import com.motadev.clone_reddit.messaging.outbox.entity.OutboxEvent;
import com.motadev.clone_reddit.messaging.outbox.entity.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.status = :status
            ORDER BY e.createdAt ASC
            """)
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(
            @Param("status") OutboxEventStatus status,
            Pageable pageable
    );
}
