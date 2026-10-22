package com.motadev.clone_reddit.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.4-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresqlContainer() {
        return new PostgreSQLContainer<>("postgres:latest");
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitMQContainer() {
        return new RabbitMQContainer("rabbitmq:4-management");
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
    }

    @Bean
    DynamicPropertyRegistrar redisProperties(GenericContainer<?> redisContainer) {
        return registry -> {
            registry.add("spring.data.redis.host", redisContainer::getHost);
            registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        };
    }
}
