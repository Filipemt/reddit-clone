package com.motadev.clone_reddit.notification.service;

import com.motadev.clone_reddit.notification.dto.RenderedNotificationEmail;

import java.util.Map;

public interface NotificationEmailTemplateRendererI {

    RenderedNotificationEmail render(String notificationType, Map<String, Object> variables);
}
