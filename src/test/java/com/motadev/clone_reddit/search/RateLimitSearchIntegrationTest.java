package com.motadev.clone_reddit.search;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "12121212-1212-1212-1212-121212121212")
    void searchReturns429AfterPolicyLimit() throws Exception {
        mockMvc.perform(get("/search").param("q", "java"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/search").param("q", "spring"))
                .andExpect(status().isOk());

        MvcResult blocked = mockMvc.perform(get("/search").param("q", "redis"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Too many requests. Try again later."))
                .andReturn();

        assertThat(blocked.getResponse().getHeader(RateLimitHeaders.LIMIT)).isEqualTo("2");
        assertThat(blocked.getResponse().getHeader(RateLimitHeaders.REMAINING)).isEqualTo("0");
        assertThat(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }
}
