package com.motadev.clone_reddit.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqTopologyConfig {

    private final RabbitMqProperties properties;

    public RabbitMqTopologyConfig(RabbitMqProperties properties) {
        this.properties = properties;
    }

    @Bean
    public TopicExchange domainEventsExchange() {
        return new TopicExchange(properties.exchange(), true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(properties.notificationQueue())
                .withArgument("x-dead-letter-exchange", properties.exchange())
                .withArgument("x-dead-letter-routing-key", properties.notificationDlqRoutingKey())
                .build();
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(properties.notificationDlq()).build();
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(domainEventsExchange)
                .with(properties.notificationRoutingKey());
    }

    @Bean
    public Binding notificationDeadLetterBinding(
            Queue notificationDeadLetterQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(domainEventsExchange)
                .with(properties.notificationDlqRoutingKey());
    }

    @Bean
    public MessageConverter jacksonAmqpMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
