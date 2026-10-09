package com.motadev.clone_reddit.messaging.config;

import com.motadev.clone_reddit.messaging.logging.MessagingEventLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitMqTopologyReadyListenerTest {

    private static final RabbitMqProperties PROPERTIES = new RabbitMqProperties(
            "clone-reddit.events",
            "notification.events",
            "notification.#",
            "notification.events.dlq",
            "notification.dlq"
    );

    @Mock
    private RabbitAdmin rabbitAdmin;

    @Mock
    private MessagingEventLog messagingEventLog;

    private RabbitMqTopologyReadyListener listener;

    @BeforeEach
    void setUp() {
        listener = new RabbitMqTopologyReadyListener(rabbitAdmin, PROPERTIES, messagingEventLog);
    }

    @Test
    void declaresTopologyAndLogsReadyWhenQueuesExist() {
        when(rabbitAdmin.getQueueInfo("notification.events"))
                .thenReturn(new QueueInformation("notification.events", 0, 0));
        when(rabbitAdmin.getQueueInfo("notification.events.dlq"))
                .thenReturn(new QueueInformation("notification.events.dlq", 0, 0));

        listener.onReady();

        verify(rabbitAdmin).initialize();
        verify(messagingEventLog).topologyReady(
                "clone-reddit.events",
                "notification.events",
                "notification.events.dlq"
        );
        verify(messagingEventLog, never()).topologyFailed(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void failsWhenQueueMissingAfterInitialize() {
        when(rabbitAdmin.getQueueInfo("notification.events")).thenReturn(null);
        when(rabbitAdmin.getQueueInfo("notification.events.dlq"))
                .thenReturn(new QueueInformation("notification.events.dlq", 0, 0));

        assertThatThrownBy(listener::onReady)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not declared");

        verify(messagingEventLog).topologyFailed(
                "clone-reddit.events",
                "notification.events",
                "notification.events.dlq",
                "queue_missing_after_declare"
        );
        verify(messagingEventLog, never()).topologyReady(anyString(), anyString(), anyString());
    }

    @Test
    void failsWhenBrokerUnreachable() {
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitAdmin)
                .initialize();

        assertThatThrownBy(listener::onReady)
                .isInstanceOf(AmqpConnectException.class);

        verify(messagingEventLog).topologyFailed(
                "clone-reddit.events",
                "notification.events",
                "notification.events.dlq",
                "AmqpConnectException"
        );
        verify(messagingEventLog, never()).topologyReady(anyString(), anyString(), anyString());
    }
}
