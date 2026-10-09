package com.motadev.clone_reddit.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq")
public record RabbitMqProperties(
        String exchange,
        String notificationQueue,
        String notificationRoutingKey,
        String notificationDlq,
        String notificationDlqRoutingKey
) {

    public RabbitMqProperties {
        if (exchange == null || exchange.isBlank()) {
            throw new IllegalArgumentException("app.rabbitmq.exchange must not be blank");
        }
        if (notificationQueue == null || notificationQueue.isBlank()) {
            throw new IllegalArgumentException("app.rabbitmq.notification-queue must not be blank");
        }
        if (notificationRoutingKey == null || notificationRoutingKey.isBlank()) {
            throw new IllegalArgumentException("app.rabbitmq.notification-routing-key must not be blank");
        }
        if (notificationDlq == null || notificationDlq.isBlank()) {
            throw new IllegalArgumentException("app.rabbitmq.notification-dlq must not be blank");
        }
        if (notificationDlqRoutingKey == null || notificationDlqRoutingKey.isBlank()) {
            throw new IllegalArgumentException("app.rabbitmq.notification-dlq-routing-key must not be blank");
        }
    }
}
