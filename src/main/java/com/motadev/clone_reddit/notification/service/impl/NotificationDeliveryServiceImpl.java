package com.motadev.clone_reddit.notification.service.impl;

import com.motadev.clone_reddit.notification.config.NotificationEmailJobProperties;
import com.motadev.clone_reddit.notification.dto.RenderedNotificationEmail;
import com.motadev.clone_reddit.notification.entity.Notification;
import com.motadev.clone_reddit.notification.entity.NotificationDeliveryStatus;
import com.motadev.clone_reddit.notification.logging.NotificationEventLog;
import com.motadev.clone_reddit.notification.repository.NotificationRepository;
import com.motadev.clone_reddit.notification.service.NotificationDeliveryServiceI;
import com.motadev.clone_reddit.notification.service.NotificationEmailSenderI;
import com.motadev.clone_reddit.notification.service.NotificationEmailTemplateRendererI;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.user.dtos.response.UserResponseDTO;
import com.motadev.clone_reddit.user.service.UserServiceI;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Service
public class NotificationDeliveryServiceImpl implements NotificationDeliveryServiceI {

    private final NotificationRepository notificationRepository;
    private final UserServiceI userServiceI;
    private final NotificationEmailTemplateRendererI templateRenderer;
    private final NotificationEmailSenderI emailSender;
    private final NotificationEmailJobProperties jobProperties;
    private final NotificationEventLog notificationEventLog;
    private final ObjectMapper objectMapper;

    public NotificationDeliveryServiceImpl(
            NotificationRepository notificationRepository,
            UserServiceI userServiceI,
            NotificationEmailTemplateRendererI templateRenderer,
            NotificationEmailSenderI emailSender,
            NotificationEmailJobProperties jobProperties,
            NotificationEventLog notificationEventLog,
            ObjectMapper objectMapper
    ) {
        this.notificationRepository = notificationRepository;
        this.userServiceI = userServiceI;
        this.templateRenderer = templateRenderer;
        this.emailSender = emailSender;
        this.jobProperties = jobProperties;
        this.notificationEventLog = notificationEventLog;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean deliver(Notification notification) {
        UserResponseDTO recipient;
        try {
            recipient = userServiceI.getUserById(notification.getRecipientId());
        } catch (ResourceNotFoundException ex) {
            markRecipientInvalid(notification, "recipient_not_found");
            return false;
        }

        if (recipient.email() == null || recipient.email().isBlank()) {
            markRecipientInvalid(notification, "recipient_email_missing");
            return false;
        }

        try {
            Map<String, Object> variables = buildTemplateVariables(notification, recipient.username());
            RenderedNotificationEmail rendered = templateRenderer.render(notification.getType(), variables);
            emailSender.send(recipient.email(), rendered.subject(), rendered.htmlBody());

            notification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
            notification.setAttempts(notification.getAttempts() + 1);
            notification.setSentAt(LocalDateTime.now());
            notification.setLastError(null);
            notificationRepository.save(notification);
            notificationEventLog.emailSent(
                    notification.getNotificationId(),
                    notification.getRecipientId(),
                    notification.getType()
            );
            return true;
        } catch (RuntimeException ex) {
            int attempts = notification.getAttempts() + 1;
            notification.setAttempts(attempts);
            notification.setLastError(truncate(ex.getMessage()));
            if (attempts >= jobProperties.maxAttempts()) {
                notification.setDeliveryStatus(NotificationDeliveryStatus.FAILED);
                notificationEventLog.emailFailed(
                        notification.getNotificationId(),
                        notification.getRecipientId(),
                        notification.getType(),
                        attempts,
                        ex
                );
            } else {
                notificationEventLog.emailRetry(
                        notification.getNotificationId(),
                        notification.getRecipientId(),
                        notification.getType(),
                        attempts,
                        ex
                );
            }
            notificationRepository.save(notification);
            return false;
        }
    }

    private void markRecipientInvalid(Notification notification, String reason) {
        notification.setDeliveryStatus(NotificationDeliveryStatus.FAILED);
        notification.setLastError(truncate(reason));
        notificationRepository.save(notification);
        notificationEventLog.emailRecipientInvalid(
                notification.getNotificationId(),
                notification.getRecipientId(),
                notification.getType(),
                reason
        );
    }

    private Map<String, Object> buildTemplateVariables(Notification notification, String username) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("username", username);
        variables.put("notificationType", notification.getType());
        try {
            JsonNode node = objectMapper.readTree(
                    notification.getPayload() == null ? "{}" : notification.getPayload()
            );
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    continue;
                }
                if (value.isValueNode()) {
                    variables.put(entry.getKey(), value.asString());
                } else {
                    variables.put(entry.getKey(), value.toString());
                }
            }
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid notification payload", ex);
        }
        return variables;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
