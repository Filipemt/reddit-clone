package com.motadev.clone_reddit.notification.service.impl;

import com.motadev.clone_reddit.notification.config.NotificationEmailJobProperties;
import com.motadev.clone_reddit.notification.entity.Notification;
import com.motadev.clone_reddit.notification.entity.NotificationDeliveryStatus;
import com.motadev.clone_reddit.notification.logging.NotificationEventLog;
import com.motadev.clone_reddit.notification.repository.NotificationRepository;
import com.motadev.clone_reddit.notification.service.NotificationDeliveryServiceI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEmailJobTest {

    private static final long ADVISORY_LOCK_ID = 876543210L;
    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationDeliveryServiceI deliveryService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private TransactionTemplate transactionTemplate;

    private NotificationEmailJob job;

    @BeforeEach
    void setUp() {
        job = jobWith(true);
    }

    @Test
    void deliverPending_whenDisabled_skipsLockAndWork() {
        job = jobWith(false);

        job.deliverPending();

        verifyNoInteractions(jdbcTemplate, notificationRepository, deliveryService, transactionTemplate);
    }

    @Test
    void deliverPending_whenLockNotAcquired_skipsWorkAndDoesNotUnlock() {
        when(jdbcTemplate.queryForObject(TRY_LOCK_SQL, Boolean.class, ADVISORY_LOCK_ID)).thenReturn(false);

        job.deliverPending();

        verify(jdbcTemplate, never()).queryForObject(UNLOCK_SQL, Boolean.class, ADVISORY_LOCK_ID);
        verifyNoInteractions(notificationRepository, deliveryService, transactionTemplate);
    }

    @Test
    void deliverPending_whenLockAcquired_deliversBatchAndReleasesLock() {
        Notification notification = new Notification();
        notification.setNotificationId(UUID.randomUUID());
        notification.setDeliveryStatus(NotificationDeliveryStatus.PENDING);

        when(jdbcTemplate.queryForObject(TRY_LOCK_SQL, Boolean.class, ADVISORY_LOCK_ID)).thenReturn(true);
        when(notificationRepository.findByDeliveryStatusOrderByCreatedAtAsc(
                eq(NotificationDeliveryStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(List.of(notification));
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any());
        when(deliveryService.deliver(notification)).thenReturn(true);

        job.deliverPending();

        verify(deliveryService).deliver(notification);
        verify(jdbcTemplate).queryForObject(UNLOCK_SQL, Boolean.class, ADVISORY_LOCK_ID);
    }

    private NotificationEmailJob jobWith(boolean enabled) {
        return new NotificationEmailJob(
                new NotificationEmailJobProperties(enabled, 50, ADVISORY_LOCK_ID, 5),
                notificationRepository,
                deliveryService,
                jdbcTemplate,
                transactionTemplate,
                new NotificationEventLog()
        );
    }
}
