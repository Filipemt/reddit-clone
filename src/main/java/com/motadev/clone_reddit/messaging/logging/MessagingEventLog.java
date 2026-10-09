package com.motadev.clone_reddit.messaging.logging;

import com.motadev.clone_reddit.messaging.config.RabbitMqTopologyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MessagingEventLog {

    private final Logger log = LoggerFactory.getLogger(RabbitMqTopologyConfig.class);

    public void topologyReady(String exchange, String notificationQueue, String notificationDlq) {
        log.atInfo()
                .addKeyValue("event", "messaging.topology.ready")
                .addKeyValue("exchange", exchange)
                .addKeyValue("notificationQueue", notificationQueue)
                .addKeyValue("notificationDlq", notificationDlq)
                .setMessage("RabbitMQ topology beans registered")
                .log();
    }
}
