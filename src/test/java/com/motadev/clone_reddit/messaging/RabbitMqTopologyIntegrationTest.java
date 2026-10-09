package com.motadev.clone_reddit.messaging;

import com.motadev.clone_reddit.messaging.config.RabbitMqProperties;
import com.motadev.clone_reddit.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RabbitMqTopologyIntegrationTest {

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitMqProperties properties;

    @Autowired
    private TopicExchange domainEventsExchange;

    @Test
    void declaresExchangeAndNotificationQueues() {
        assertThat(domainEventsExchange.getName()).isEqualTo(properties.exchange());

        QueueInformation notificationQueue = amqpAdmin.getQueueInfo(properties.notificationQueue());
        QueueInformation dlq = amqpAdmin.getQueueInfo(properties.notificationDlq());

        assertThat(notificationQueue).isNotNull();
        assertThat(notificationQueue.getName()).isEqualTo(properties.notificationQueue());
        assertThat(dlq).isNotNull();
        assertThat(dlq.getName()).isEqualTo(properties.notificationDlq());
    }
}
