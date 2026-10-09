package com.motadev.clone_reddit.messaging.outbox.service.impl;

import com.motadev.clone_reddit.messaging.logging.MessagingEventLog;
import com.motadev.clone_reddit.messaging.outbox.entity.OutboxEvent;
import com.motadev.clone_reddit.messaging.outbox.entity.OutboxEventStatus;
import com.motadev.clone_reddit.messaging.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceImplTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private MessagingEventLog messagingEventLog;

    private OutboxServiceImpl outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxServiceImpl(outboxEventRepository, new ObjectMapper(), messagingEventLog);
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enqueuePersistsPendingEventWithSerializedPayload() {
        UUID aggregateId = UUID.randomUUID();

        UUID eventId = outboxService.enqueue(
                "post",
                aggregateId,
                "notification.post.created",
                "notification.post.created",
                Map.of("communityId", aggregateId.toString())
        );

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(eventId).isEqualTo(saved.getEventId());
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getAggregateType()).isEqualTo("post");
        assertThat(saved.getAggregateId()).isEqualTo(aggregateId);
        assertThat(saved.getRoutingKey()).isEqualTo("notification.post.created");
        assertThat(saved.getPayload()).contains("communityId");
        verify(messagingEventLog).outboxEnqueued(
                saved.getEventId(),
                "notification.post.created",
                "notification.post.created",
                aggregateId
        );
    }
}
