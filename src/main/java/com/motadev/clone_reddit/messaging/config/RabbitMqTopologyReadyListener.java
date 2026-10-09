package com.motadev.clone_reddit.messaging.config;

import com.motadev.clone_reddit.messaging.logging.MessagingEventLog;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqTopologyReadyListener {

    private final AmqpAdmin amqpAdmin;
    private final RabbitMqProperties properties;
    private final MessagingEventLog messagingEventLog;

    public RabbitMqTopologyReadyListener(
            AmqpAdmin amqpAdmin,
            RabbitMqProperties properties,
            MessagingEventLog messagingEventLog
    ) {
        this.amqpAdmin = amqpAdmin;
        this.properties = properties;
        this.messagingEventLog = messagingEventLog;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            declareTopology();
            QueueInformation notificationQueue = amqpAdmin.getQueueInfo(properties.notificationQueue());
            QueueInformation dlq = amqpAdmin.getQueueInfo(properties.notificationDlq());
            if (notificationQueue == null || dlq == null) {
                messagingEventLog.topologyFailed(
                        properties.exchange(),
                        properties.notificationQueue(),
                        properties.notificationDlq(),
                        "queue_missing_after_declare"
                );
                throw new IllegalStateException(
                        "RabbitMQ topology was not declared on the broker after initialize()"
                );
            }
            messagingEventLog.topologyReady(
                    properties.exchange(),
                    properties.notificationQueue(),
                    properties.notificationDlq()
            );
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            messagingEventLog.topologyFailed(
                    properties.exchange(),
                    properties.notificationQueue(),
                    properties.notificationDlq(),
                    ex.getClass().getSimpleName()
            );
            throw ex;
        }
    }

    private void declareTopology() {
        if (!(amqpAdmin instanceof RabbitAdmin rabbitAdmin)) {
            throw new IllegalStateException("AmqpAdmin must be a RabbitAdmin to declare topology");
        }
        rabbitAdmin.initialize();
    }
}
