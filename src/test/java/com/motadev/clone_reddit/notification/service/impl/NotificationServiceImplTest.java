package com.motadev.clone_reddit.notification.service.impl;

import com.motadev.clone_reddit.messaging.outbox.dto.OutboxMessageEnvelope;
import com.motadev.clone_reddit.notification.entity.Notification;
import com.motadev.clone_reddit.notification.entity.NotificationDeliveryStatus;
import com.motadev.clone_reddit.notification.logging.NotificationEventLog;
import com.motadev.clone_reddit.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(
                notificationRepository,
                new ObjectMapper(),
                new NotificationEventLog()
        );
    }

    @Test
    void ingestPersistsNotificationForOwnerId() {
        when(notificationRepository.existsByEventId(EVENT_ID)).thenReturn(false);

        var envelope = new OutboxMessageEnvelope(
                EVENT_ID,
                "notification.post.created",
                "post",
                UUID.randomUUID(),
                Instant.now(),
                "{\"ownerId\":\"" + USER_ID + "\",\"title\":\"hi\"}"
        );

        service.ingest(envelope);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getType()).isEqualTo("notification.post.created");
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(captor.getValue().getAttempts()).isZero();
    }

    @Test
    void ingestSkipsDuplicates() {
        when(notificationRepository.existsByEventId(EVENT_ID)).thenReturn(true);

        service.ingest(new OutboxMessageEnvelope(
                EVENT_ID, "notification.vote.upvoted", "vote", UUID.randomUUID(), Instant.now(), "{}"
        ));

        verify(notificationRepository, never()).save(any());
    }
}
