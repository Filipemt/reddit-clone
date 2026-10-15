package com.motadev.clone_reddit.notification.service.impl;

import com.motadev.clone_reddit.notification.config.NotificationEmailJobProperties;
import com.motadev.clone_reddit.notification.dto.RenderedNotificationEmail;
import com.motadev.clone_reddit.notification.entity.Notification;
import com.motadev.clone_reddit.notification.entity.NotificationDeliveryStatus;
import com.motadev.clone_reddit.notification.logging.NotificationEventLog;
import com.motadev.clone_reddit.notification.repository.NotificationRepository;
import com.motadev.clone_reddit.notification.service.NotificationEmailSenderI;
import com.motadev.clone_reddit.notification.service.NotificationEmailTemplateRendererI;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.user.dtos.response.UserResponseDTO;
import com.motadev.clone_reddit.user.service.UserServiceI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceImplTest {

    private static final UUID NOTIFICATION_ID = UUID.randomUUID();
    private static final UUID RECIPIENT_ID = UUID.randomUUID();

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserServiceI userServiceI;
    @Mock
    private NotificationEmailTemplateRendererI templateRenderer;
    @Mock
    private NotificationEmailSenderI emailSender;

    private NotificationDeliveryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationDeliveryServiceImpl(
                notificationRepository,
                userServiceI,
                templateRenderer,
                emailSender,
                new NotificationEmailJobProperties(true, 50, 876543210L, 5),
                new NotificationEventLog(),
                new ObjectMapper()
        );
    }

    @Test
    void deliverMarksSentOnSuccess() {
        Notification notification = pendingNotification();
        when(userServiceI.getUserById(RECIPIENT_ID))
                .thenReturn(new UserResponseDTO(RECIPIENT_ID, "alice", "alice@example.com", 0));
        when(templateRenderer.render(eq("notification.post.created"), anyMap()))
                .thenReturn(new RenderedNotificationEmail("Subject", "<p>body</p>"));

        boolean ok = service.deliver(notification);

        assertThat(ok).isTrue();
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
        assertThat(captor.getValue().getSentAt()).isNotNull();
        verify(emailSender).send("alice@example.com", "Subject", "<p>body</p>");
    }

    @Test
    void deliverKeepsPendingOnSmtpFailureBeforeMaxAttempts() {
        Notification notification = pendingNotification();
        when(userServiceI.getUserById(RECIPIENT_ID))
                .thenReturn(new UserResponseDTO(RECIPIENT_ID, "alice", "alice@example.com", 0));
        when(templateRenderer.render(anyString(), anyMap()))
                .thenReturn(new RenderedNotificationEmail("Subject", "<p>body</p>"));
        doThrow(new IllegalStateException("smtp down")).when(emailSender).send(anyString(), anyString(), anyString());

        boolean ok = service.deliver(notification);

        assertThat(ok).isFalse();
        assertThat(notification.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getLastError()).contains("smtp down");
        verify(notificationRepository).save(notification);
    }

    @Test
    void deliverMarksFailedWhenMaxAttemptsReached() {
        Notification notification = pendingNotification();
        notification.setAttempts(4);
        when(userServiceI.getUserById(RECIPIENT_ID))
                .thenReturn(new UserResponseDTO(RECIPIENT_ID, "alice", "alice@example.com", 0));
        when(templateRenderer.render(anyString(), anyMap()))
                .thenReturn(new RenderedNotificationEmail("Subject", "<p>body</p>"));
        doThrow(new IllegalStateException("smtp down")).when(emailSender).send(anyString(), anyString(), anyString());

        boolean ok = service.deliver(notification);

        assertThat(ok).isFalse();
        assertThat(notification.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(notification.getAttempts()).isEqualTo(5);
        verify(notificationRepository).save(notification);
    }

    @Test
    void deliverMarksFailedWhenRecipientMissing() {
        Notification notification = pendingNotification();
        when(userServiceI.getUserById(RECIPIENT_ID)).thenThrow(new ResourceNotFoundException("User not found."));

        boolean ok = service.deliver(notification);

        assertThat(ok).isFalse();
        assertThat(notification.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(notification.getLastError()).isEqualTo("recipient_not_found");
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
        verify(templateRenderer, never()).render(anyString(), any());
    }

    @Test
    void deliverMarksFailedWhenRecipientEmailBlank() {
        Notification notification = pendingNotification();
        when(userServiceI.getUserById(RECIPIENT_ID))
                .thenReturn(new UserResponseDTO(RECIPIENT_ID, "alice", "  ", 0));

        boolean ok = service.deliver(notification);

        assertThat(ok).isFalse();
        assertThat(notification.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(notification.getLastError()).isEqualTo("recipient_email_missing");
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    private static Notification pendingNotification() {
        Notification notification = new Notification();
        notification.setNotificationId(NOTIFICATION_ID);
        notification.setEventId(UUID.randomUUID());
        notification.setRecipientId(RECIPIENT_ID);
        notification.setType("notification.post.created");
        notification.setPayload("{\"title\":\"hello\",\"postId\":\"" + UUID.randomUUID() + "\"}");
        notification.setDeliveryStatus(NotificationDeliveryStatus.PENDING);
        notification.setAttempts(0);
        return notification;
    }
}
