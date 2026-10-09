package com.motadev.clone_reddit.community;

import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.community.entity.enums.CommunityTypeEnum;
import com.motadev.clone_reddit.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CommunityMembershipIntegrationTest {

    private static final String PASSWORD = "password123";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestRestTemplate rest;

    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    @BeforeEach
    void setUp() {
        rest = new TestRestTemplate();
        rest.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:" + port));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void joinAndLeaveAreIdempotentAndKeepMemberCountConsistent() {
        UUID communityId = createCommunity(registerAndGetId(uniqueUser()), CommunityTypeEnum.PUBLIC);
        String token = registerAndLogin(uniqueUser());

        assertThat(join(token, communityId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(join(token, communityId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(memberCount(communityId)).isEqualTo(1);
        assertThat(membershipRows(communityId)).isEqualTo(1);

        assertThat(leave(token, communityId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(leave(token, communityId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(memberCount(communityId)).isZero();
        assertThat(membershipRows(communityId)).isZero();
    }

    @Test
    void restrictedCommunityAcceptsMembers() {
        UUID communityId = createCommunity(registerAndGetId(uniqueUser()), CommunityTypeEnum.RESTRICTED);
        String token = registerAndLogin(uniqueUser());

        assertThat(join(token, communityId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(memberCount(communityId)).isEqualTo(1);
    }

    @Test
    void privateCommunityRejectsDirectJoin() {
        UUID communityId = createCommunity(registerAndGetId(uniqueUser()), CommunityTypeEnum.PRIVATE);
        String token = registerAndLogin(uniqueUser());

        assertThat(join(token, communityId).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(memberCount(communityId)).isZero();
        assertThat(membershipRows(communityId)).isZero();
    }

    @Test
    void ownerCannotLeaveOwnCommunity() {
        String owner = uniqueUser();
        UUID communityId = createCommunity(registerAndGetId(owner), CommunityTypeEnum.PUBLIC);

        assertThat(leave(login(owner), communityId).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unknownCommunityReturnsNotFound() {
        String token = registerAndLogin(uniqueUser());

        assertThat(join(token, UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(leave(token, UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void concurrentJoinsOfSameUserCountOnce() throws Exception {
        UUID communityId = createCommunity(registerAndGetId(uniqueUser()), CommunityTypeEnum.PUBLIC);
        String token = registerAndLogin(uniqueUser());

        List<Callable<HttpStatus>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            calls.add(() -> HttpStatus.valueOf(join(token, communityId).getStatusCode().value()));
        }

        for (Future<HttpStatus> result : executor.invokeAll(calls)) {
            assertThat(result.get()).isEqualTo(HttpStatus.NO_CONTENT);
        }
        assertThat(memberCount(communityId)).isEqualTo(1);
        assertThat(membershipRows(communityId)).isEqualTo(1);
    }

    @Test
    void concurrentJoinsOfDifferentUsersAreAllCounted() throws Exception {
        UUID communityId = createCommunity(registerAndGetId(uniqueUser()), CommunityTypeEnum.PUBLIC);

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tokens.add(registerAndLogin(uniqueUser()));
        }

        List<Callable<HttpStatus>> calls = new ArrayList<>();
        for (String token : tokens) {
            calls.add(() -> HttpStatus.valueOf(join(token, communityId).getStatusCode().value()));
        }

        for (Future<HttpStatus> result : executor.invokeAll(calls)) {
            assertThat(result.get()).isEqualTo(HttpStatus.NO_CONTENT);
        }
        assertThat(memberCount(communityId)).isEqualTo(tokens.size());
        assertThat(membershipRows(communityId)).isEqualTo(tokens.size());
    }

    private ResponseEntity<Void> join(String token, UUID communityId) {
        return rest.exchange("/communities/{id}/membership", HttpMethod.PUT,
                new HttpEntity<>(bearer(token)), Void.class, communityId);
    }

    private ResponseEntity<Void> leave(String token, UUID communityId) {
        return rest.exchange("/communities/{id}/membership", HttpMethod.DELETE,
                new HttpEntity<>(bearer(token)), Void.class, communityId);
    }

    private UUID createCommunity(UUID ownerId, CommunityTypeEnum type) {
        UUID communityId = UUID.randomUUID();
        String slug = "c-" + communityId.toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO tb_community (community_id, name, slug, description, topic_id, type_id, status_id,
                                          owner_id, created_at)
                VALUES (?, ?, ?, 'Integration test community', 1, ?, 1, ?, now())
                """, communityId, slug, slug, type.getId(), ownerId);
        return communityId;
    }

    private long memberCount(UUID communityId) {
        return jdbcTemplate.queryForObject(
                "SELECT member_count FROM tb_community WHERE community_id = ?", Long.class, communityId);
    }

    private long membershipRows(UUID communityId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_community_membership WHERE community_id = ?", Long.class, communityId);
    }

    private UUID registerAndGetId(String username) {
        register(username);
        return jdbcTemplate.queryForObject(
                "SELECT user_id FROM tb_users WHERE username = ?", UUID.class, username);
    }

    private String registerAndLogin(String username) {
        register(username);
        return login(username);
    }

    private void register(String username) {
        ResponseEntity<Void> response = rest.exchange("/users/register", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "username", username,
                        "email", username + "@example.com",
                        "password", PASSWORD), jsonHeaders()),
                Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String login(String username) {
        ResponseEntity<TokenData> response = rest.postForEntity("/authentication/login",
                new HttpEntity<>(Map.of("username", username, "password", PASSWORD), jsonHeaders()),
                TokenData.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().accessToken();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String uniqueUser() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
