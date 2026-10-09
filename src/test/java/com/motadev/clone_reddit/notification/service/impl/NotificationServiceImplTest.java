package com.motadev.clone_reddit.notification.service.impl;

import com.motadev.clone_reddit.auth.logging.AuthEventLog;
import com.motadev.clone_reddit.messaging.outbox.dto.OutboxMessageEnvelope;
import com.motadev.clone_reddit.notification.entity.Notification;
import com.motadev.clone_reddit.notification.logging.NotificationEventLog;
import com.motadev.clone_reddit.notification.repository.NotificationRepository;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
                new AuthenticatedUserProvider(new AuthEventLog()),
                new ObjectMapper(),
                new NotificationEventLog()
        );
        setAuthenticatedUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
    }

    @Test
    void ingestSkipsDuplicates() {
        when(notificationRepository.existsByEventId(EVENT_ID)).thenReturn(true);

        service.ingest(new OutboxMessageEnvelope(
                EVENT_ID, "notification.vote.upvoted", "vote", UUID.randomUUID(), Instant.now(), "{}"
        ));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markReadSetsTimestamp() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setNotificationId(notificationId);
        notification.setRecipientId(USER_ID);
        when(notificationRepository.findByNotificationIdAndRecipientId(notificationId, USER_ID))
                .thenReturn(Optional.of(notification));

        service.markRead(notificationId);

        assertThat(notification.getReadAt()).isNotNull();
    }

    private static void setAuthenticatedUser(UUID userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                "n/a",
                List.of(new SimpleGrantedAuthority("SCOPE_BASIC"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
