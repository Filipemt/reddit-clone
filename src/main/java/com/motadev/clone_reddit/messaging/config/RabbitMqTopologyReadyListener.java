package com.motadev.clone_reddit.messaging.config;

import com.motadev.clone_reddit.messaging.logging.MessagingEventLog;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqTopologyReadyListener {

    private final RabbitMqProperties properties;
    private final MessagingEventLog messagingEventLog;

    public RabbitMqTopologyReadyListener(RabbitMqProperties properties, MessagingEventLog messagingEventLog) {
        this.properties = properties;
        this.messagingEventLog = messagingEventLog;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        messagingEventLog.topologyReady(
                properties.exchange(),
                properties.notificationQueue(),
                properties.notificationDlq()
        );
    }
}
