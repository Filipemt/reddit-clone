package com.motadev.clone_reddit.community;

import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.community.entity.enums.CommunityMemberRoleEnum;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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

    @Test
    void createdCommunityHasOwnerAsModerator() {
        String token = registerAndLogin(uniqueUser());

        ResponseEntity<Map> response = createCommunityViaApi(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("memberCount")).isEqualTo(1);
        assertThat(response.getBody().get("isMember")).isEqualTo(true);

        UUID communityId = UUID.fromString((String) response.getBody().get("communityId"));
        assertThat(memberCount(communityId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role_id FROM tb_community_membership WHERE community_id = ?", Long.class, communityId))
                .isEqualTo(CommunityMemberRoleEnum.MODERATOR.getId());
    }

    @Test
    void detailReportsWhetherTheUserFollowsTheCommunity() {
        UUID communityId = createCommunity(registerAndGetId(uniqueUser()), CommunityTypeEnum.PUBLIC);
        String token = registerAndLogin(uniqueUser());

        assertThat(getById(token, communityId).get("isMember")).isEqualTo(false);

        join(token, communityId);

        Map<?, ?> detail = getById(token, communityId);
        assertThat(detail.get("isMember")).isEqualTo(true);
        assertThat(detail.get("memberCount")).isEqualTo(1);
    }

    @Test
    void myCommunitiesListsFollowedCommunitiesNewestFirst() {
        UUID ownerId = registerAndGetId(uniqueUser());
        UUID first = createCommunity(ownerId, CommunityTypeEnum.PUBLIC);
        UUID second = createCommunity(ownerId, CommunityTypeEnum.PUBLIC);
        UUID left = createCommunity(ownerId, CommunityTypeEnum.PUBLIC);
        UUID removed = createCommunity(ownerId, CommunityTypeEnum.PUBLIC);
        String token = registerAndLogin(uniqueUser());

        join(token, first);
        join(token, second);
        join(token, left);
        join(token, removed);
        leave(token, left);
        jdbcTemplate.update("UPDATE tb_community SET deleted_at = now() WHERE community_id = ?", removed);
        jdbcTemplate.update("UPDATE tb_community_membership SET joined_at = now() - interval '1 hour' "
                + "WHERE community_id = ?", first);

        ResponseEntity<Map> response = rest.exchange("/communities/me", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).extracting(item -> item.get("communityId"))
                .containsExactly(second.toString(), first.toString());
        assertThat(content).allSatisfy(item -> assertThat(item.get("isMember")).isEqualTo(true));
    }

    private ResponseEntity<Map> createCommunityViaApi(String token) {
        String slug = "c-" + UUID.randomUUID().toString().substring(0, 8);

        HttpHeaders dataHeaders = new HttpHeaders();
        dataHeaders.setContentType(MediaType.APPLICATION_JSON);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("data", new HttpEntity<>(Map.of(
                "name", slug,
                "slug", slug,
                "description", "Integration test community",
                "topicId", 1,
                "typeId", CommunityTypeEnum.PUBLIC.getId(),
                "statusId", 1), dataHeaders));

        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/communities", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private Map<?, ?> getById(String token, UUID communityId) {
        ResponseEntity<Map> response = rest.exchange("/communities/{id}", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), Map.class, communityId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
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
