package com.motadev.clone_reddit.shared.ratelimit;

import com.motadev.clone_reddit.shared.ratelimit.web.RateLimitHeaders;
import com.motadev.clone_reddit.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RateLimitFixtureController.class})
class RateLimitFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void allowsUntilLimitThenReturns429WithHeaders() throws Exception {
        String uniqueIp = "203.0.113." + (UUID.randomUUID().hashCode() & 0xff);

        MvcResult first = mockMvc.perform(get("/__ratelimit_fixture/ping").with(request -> {
                    request.setRemoteAddr(uniqueIp);
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andReturn();

        assertThat(first.getResponse().getHeader(RateLimitHeaders.LIMIT)).isEqualTo("2");
        assertThat(first.getResponse().getHeader(RateLimitHeaders.REMAINING)).isEqualTo("1");
        assertThat(first.getResponse().getHeader(RateLimitHeaders.RESET)).isNotBlank();

        mockMvc.perform(get("/__ratelimit_fixture/ping").with(request -> {
                    request.setRemoteAddr(uniqueIp);
                    return request;
                }))
                .andExpect(status().isOk());

        MvcResult blocked = mockMvc.perform(get("/__ratelimit_fixture/ping").with(request -> {
                    request.setRemoteAddr(uniqueIp);
                    return request;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Too many requests. Try again later."))
                .andReturn();

        assertThat(blocked.getResponse().getHeader(RateLimitHeaders.LIMIT)).isEqualTo("2");
        assertThat(blocked.getResponse().getHeader(RateLimitHeaders.REMAINING)).isEqualTo("0");
        assertThat(blocked.getResponse().getHeader(RateLimitHeaders.RESET)).isNotBlank();
        assertThat(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }
}
