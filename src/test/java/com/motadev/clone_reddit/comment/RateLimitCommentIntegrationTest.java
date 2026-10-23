package com.motadev.clone_reddit.comment;

import com.motadev.clone_reddit.shared.ratelimit.web.RateLimitHeaders;
import com.motadev.clone_reddit.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitCommentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "dddddddd-dddd-dddd-dddd-dddddddddddd")
    void createCommentReturns429AfterPolicyLimit() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(post("/posts/{postId}/comments", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"hello"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/posts/{postId}/comments", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"hello"}
                                """))
                .andExpect(status().isNotFound());

        MvcResult blocked = mockMvc.perform(post("/posts/{postId}/comments", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"hello"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andReturn();

        assertThat(blocked.getResponse().getHeader(RateLimitHeaders.LIMIT)).isEqualTo("2");
        assertThat(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }

    @Test
    @WithMockUser(username = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")
    void listCommentTreeReturns429AfterPolicyLimit() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(get("/posts/{postId}/comments", postId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/posts/{postId}/comments", postId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/posts/{postId}/comments", postId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many requests. Try again later."));
    }
}
