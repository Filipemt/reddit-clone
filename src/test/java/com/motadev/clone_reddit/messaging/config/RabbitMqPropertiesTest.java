package com.motadev.clone_reddit.messaging.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RabbitMqPropertiesTest {

    @Test
    void acceptsValidTopologyNames() {
        var properties = new RabbitMqProperties(
                "clone-reddit.events",
                "notification.events",
                "notification.#",
                "notification.events.dlq",
                "notification.dlq"
        );

        assertThat(properties.exchange()).isEqualTo("clone-reddit.events");
        assertThat(properties.notificationRoutingKey()).isEqualTo("notification.#");
    }

    @Test
    void rejectsBlankExchange() {
        assertThatThrownBy(() -> new RabbitMqProperties(
                " ",
                "notification.events",
                "notification.#",
                "notification.events.dlq",
                "notification.dlq"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exchange");
    }
}
