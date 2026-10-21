package com.motadev.clone_reddit.shared.ratelimit;

import com.motadev.clone_reddit.shared.ratelimit.store.RateLimitDecision;
import com.motadev.clone_reddit.shared.ratelimit.store.RedisRateLimitStore;
import com.motadev.clone_reddit.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RedisRateLimitStoreIntegrationTest {

    @Autowired
    private RedisRateLimitStore redisRateLimitStore;

    @Test
    void consumesUntilLimitThenDenies() {
        String key = "ratelimit:store-test:" + UUID.randomUUID();

        RateLimitDecision first = redisRateLimitStore.consume(key, 2, 60);
        RateLimitDecision second = redisRateLimitStore.consume(key, 2, 60);
        RateLimitDecision third = redisRateLimitStore.consume(key, 2, 60);

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(1);
        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isEqualTo(0);
        assertThat(third.allowed()).isFalse();
        assertThat(third.remaining()).isEqualTo(0);
        assertThat(third.retryAfterSeconds()).isPositive();
        assertThat(third.resetEpochSeconds()).isGreaterThan(System.currentTimeMillis() / 1000L);
    }
}
