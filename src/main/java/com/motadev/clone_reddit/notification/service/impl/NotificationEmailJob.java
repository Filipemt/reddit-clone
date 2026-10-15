package com.motadev.clone_reddit.notification.service.impl;

import com.motadev.clone_reddit.notification.config.NotificationEmailJobProperties;
import com.motadev.clone_reddit.notification.entity.Notification;
import com.motadev.clone_reddit.notification.entity.NotificationDeliveryStatus;
import com.motadev.clone_reddit.notification.logging.NotificationEventLog;
import com.motadev.clone_reddit.notification.repository.NotificationRepository;
import com.motadev.clone_reddit.notification.service.NotificationDeliveryServiceI;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class NotificationEmailJob {

    private final NotificationEmailJobProperties properties;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryServiceI deliveryService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final NotificationEventLog notificationEventLog;

    public NotificationEmailJob(
            NotificationEmailJobProperties properties,
            NotificationRepository notificationRepository,
            NotificationDeliveryServiceI deliveryService,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            NotificationEventLog notificationEventLog
    ) {
        this.properties = properties;
        this.notificationRepository = notificationRepository;
        this.deliveryService = deliveryService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.notificationEventLog = notificationEventLog;
    }

    @Scheduled(
            fixedDelayString = "${app.notification.email.fixed-delay-ms}",
            initialDelayString = "${app.notification.email.initial-delay-ms}"
    )
    public void deliverPending() {
        if (!properties.enabled()) {
            return;
        }

        if (!acquireLock()) {
            notificationEventLog.emailLockNotAcquired(properties.advisoryLockId());
            return;
        }

        int sent = 0;
        int failed = 0;
        try {
            while (true) {
                List<Notification> batch = notificationRepository.findByDeliveryStatusOrderByCreatedAtAsc(
                        NotificationDeliveryStatus.PENDING,
                        PageRequest.of(0, properties.batchSize())
                );
                if (batch.isEmpty()) {
                    break;
                }

                for (Notification notification : batch) {
                    boolean ok = Boolean.TRUE.equals(
                            transactionTemplate.execute(status -> deliveryService.deliver(notification))
                    );
                    if (ok) {
                        sent++;
                    } else {
                        failed++;
                    }
                }

                if (batch.size() < properties.batchSize()) {
                    break;
                }
            }

            if (sent > 0 || failed > 0) {
                notificationEventLog.emailBatch(sent, failed);
            }
        } finally {
            releaseLock();
        }
    }

    private boolean acquireLock() {
        Boolean result = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(?)",
                Boolean.class,
                properties.advisoryLockId()
        );
        return Boolean.TRUE.equals(result);
    }

    private void releaseLock() {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_unlock(?)",
                Boolean.class,
                properties.advisoryLockId()
        );
    }
}
