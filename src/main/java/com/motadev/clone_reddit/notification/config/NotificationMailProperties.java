package com.motadev.clone_reddit.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record NotificationMailProperties(String from) {

    public NotificationMailProperties {
        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException("app.mail.from must not be blank");
        }
    }
}
