package com.motadev.clone_reddit.post;

import com.motadev.clone_reddit.shared.ratelimit.web.RateLimitHeaders;
import com.motadev.clone_reddit.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitPostIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    void createPostReturns429AfterPolicyLimit() throws Exception {
        UUID communityId = UUID.randomUUID();
        MockMultipartFile data = new MockMultipartFile(
                "data",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {"title":"Rate limit post","body":"body"}
                        """.getBytes()
        );

        mockMvc.perform(multipart("/communities/{communityId}/posts", communityId).file(data))
                .andExpect(status().isNotFound());

        mockMvc.perform(multipart("/communities/{communityId}/posts", communityId).file(data))
                .andExpect(status().isNotFound());

        MvcResult blocked = mockMvc.perform(multipart("/communities/{communityId}/posts", communityId).file(data))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andReturn();

        assertThat(blocked.getResponse().getHeader(RateLimitHeaders.LIMIT)).isEqualTo("2");
        assertThat(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }

    @Test
    @WithMockUser(username = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    void getPostReturns429AfterPolicyLimit() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(get("/posts/{postId}", postId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/posts/{postId}", postId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/posts/{postId}", postId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many requests. Try again later."));
    }

    @Test
    @WithMockUser(username = "cccccccc-cccc-cccc-cccc-cccccccccccc")
    void listPostsReturns429AfterPolicyLimit() throws Exception {
        UUID communityId = UUID.randomUUID();

        mockMvc.perform(get("/communities/{communityId}/posts", communityId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/communities/{communityId}/posts", communityId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/communities/{communityId}/posts", communityId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }
}
