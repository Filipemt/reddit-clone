package com.motadev.clone_reddit.shared.redis;

import com.motadev.clone_reddit.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RedisConnectionIntegrationTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void setAndGetRoundTrip() {
        stringRedisTemplate.opsForValue().set("ratelimit:infra:smoke", "ok");
        assertThat(stringRedisTemplate.opsForValue().get("ratelimit:infra:smoke")).isEqualTo("ok");
    }
}
