package com.motadev.clone_reddit.vote;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitVoteIntegrationTest {

    private static final String USER_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = USER_ID)
    void votePostReturns429AfterPolicyLimit() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(put("/posts/{postId}/vote", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":1}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/posts/{postId}/vote", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":1}
                                """))
                .andExpect(status().isNotFound());

        MvcResult blocked = mockMvc.perform(put("/posts/{postId}/vote", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":1}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Too many requests. Try again later."))
                .andReturn();

        assertThat(blocked.getResponse().getHeader(RateLimitHeaders.LIMIT)).isEqualTo("2");
        assertThat(blocked.getResponse().getHeader(RateLimitHeaders.REMAINING)).isEqualTo("0");
        assertThat(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }

    @Test
    @WithMockUser(username = "ffffffff-1111-2222-3333-444444444444")
    void voteCommentReturns429AfterPolicyLimit() throws Exception {
        UUID commentId = UUID.randomUUID();

        mockMvc.perform(put("/comments/{commentId}/vote", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":-1}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/comments/{commentId}/vote", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":-1}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/comments/{commentId}/vote", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":-1}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }
}
