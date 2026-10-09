package com.motadev.clone_reddit.messaging.outbox;

import com.motadev.clone_reddit.messaging.config.RabbitMqProperties;
import com.motadev.clone_reddit.messaging.outbox.dto.OutboxMessageEnvelope;
import com.motadev.clone_reddit.messaging.outbox.entity.OutboxEvent;
import com.motadev.clone_reddit.messaging.outbox.entity.OutboxEventStatus;
import com.motadev.clone_reddit.messaging.outbox.repository.OutboxEventRepository;
import com.motadev.clone_reddit.messaging.outbox.service.OutboxServiceI;
import com.motadev.clone_reddit.messaging.outbox.service.impl.OutboxPublisherJob;
import com.motadev.clone_reddit.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.outbox.publisher.enabled=true")
@Import(TestcontainersConfiguration.class)
class OutboxPublisherIntegrationTest {

    @Autowired
    private OutboxServiceI outboxServiceI;

    @Autowired
    private OutboxPublisherJob outboxPublisherJob;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitMqProperties rabbitMqProperties;

    @Test
    void publishesEnqueuedEventToNotificationQueue() {
        UUID aggregateId = UUID.randomUUID();
        UUID eventId = outboxServiceI.enqueue(
                "post",
                aggregateId,
                "notification.post.created",
                "notification.post.created",
                Map.of("postId", aggregateId.toString(), "ownerId", UUID.randomUUID().toString())
        );

        outboxPublisherJob.publishPending();

        OutboxEvent stored = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(stored.getPublishedAt()).isNotNull();
        assertThat(stored.getAttempts()).isEqualTo(1);

        OutboxMessageEnvelope envelope = rabbitTemplate.receiveAndConvert(
                rabbitMqProperties.notificationQueue(),
                5000,
                new ParameterizedTypeReference<>() {
                }
        );

        assertThat(envelope).isNotNull();
        assertThat(envelope.eventId()).isEqualTo(eventId);
        assertThat(envelope.eventType()).isEqualTo("notification.post.created");
        assertThat(envelope.aggregateId()).isEqualTo(aggregateId);
        assertThat(envelope.payload()).contains("postId");
    }
}
