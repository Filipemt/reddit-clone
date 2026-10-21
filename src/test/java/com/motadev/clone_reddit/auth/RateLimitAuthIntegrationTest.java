package com.motadev.clone_reddit.auth;

import com.motadev.clone_reddit.shared.ratelimit.web.RateLimitHeaders;
import com.motadev.clone_reddit.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginReturns429AfterPolicyLimit() throws Exception {
        RequestPostProcessor ip = uniqueIp();

        mockMvc.perform(post("/authentication/login")
                        .with(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"carol", "password":"password123"}
                                """))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/authentication/login")
                        .with(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"carol", "password":"password123"}
                                """))
                .andExpect(status().isUnprocessableEntity());

        MvcResult blocked = mockMvc.perform(post("/authentication/login")
                        .with(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"carol", "password":"password123"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andReturn();

        assertThat(blocked.getResponse().getHeader(RateLimitHeaders.LIMIT)).isEqualTo("2");
        assertThat(blocked.getResponse().getHeader(RateLimitHeaders.REMAINING)).isEqualTo("0");
        assertThat(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }

    @Test
    void registerReturns429AfterPolicyLimit() throws Exception {
        RequestPostProcessor ip = uniqueIp();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/users/register")
                        .with(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"u1_%s", "password":"password123", "email":"u1_%s@example.com"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/users/register")
                        .with(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"u2_%s", "password":"password123", "email":"u2_%s@example.com"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/users/register")
                        .with(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"u3_%s", "password":"password123", "email":"u3_%s@example.com"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many requests. Try again later."));
    }

    @Test
    void refreshReturns429AfterPolicyLimit() throws Exception {
        RequestPostProcessor ip = uniqueIp();

        mockMvc.perform(post("/authentication/refresh")
                        .with(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/authentication/refresh")
                        .with(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/authentication/refresh")
                        .with(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    private static RequestPostProcessor uniqueIp() {
        String ip = "198.51.100." + (UUID.randomUUID().hashCode() & 0xff);
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
