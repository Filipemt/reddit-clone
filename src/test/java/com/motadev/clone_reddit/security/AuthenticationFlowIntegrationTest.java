package com.motadev.clone_reddit.security;

import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.support.TestcontainersConfiguration;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.DefaultUriBuilderFactory;

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
class AuthenticationFlowIntegrationTest {

    @LocalServerPort
    private int port;

    private TestRestTemplate rest;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @BeforeEach
    void setUp() {
        rest = new TestRestTemplate();
        rest.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:" + port));
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<Void> register(String username, String password) {
        return rest.exchange("/authentication/register", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", username, "password", password), jsonHeaders()),
                Void.class);
    }

    private TokenData login(String username, String password) {
        ResponseEntity<TokenData> response = rest.postForEntity("/authentication/login",
                new HttpEntity<>(Map.of("username", username, "password", password), jsonHeaders()),
                TokenData.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Map> loginRaw(String username, String password) {
        return rest.postForEntity("/authentication/login",
                new HttpEntity<>(Map.of("username", username, "password", password), jsonHeaders()),
                Map.class);
    }

    private ResponseEntity<TokenData> refresh(String refreshToken) {
        return rest.postForEntity("/authentication/refresh",
                new HttpEntity<>(Map.of("refreshToken", refreshToken), jsonHeaders()),
                TokenData.class);
    }

    private ResponseEntity<Void> logout(String accessToken, String refreshToken) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange("/authentication/logout", HttpMethod.DELETE,
                new HttpEntity<>(Map.of("tokenValue", refreshToken), headers),
                Void.class);
    }

    private String uniqueUser() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }

    // Fluxo feliz: registrar um usuario e garantir que ele consegue logar.
    @Test
    void registerCreatesAnAccountThatCanLogin() {
        String username = uniqueUser();
        String password = "password123";

        ResponseEntity<Void> registered = register(username, password);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        TokenData tokens = login(username, password);
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.expiresIn()).isEqualTo(300L);
    }

    @Test
    void registerRejectsDuplicateUsername() {
        String username = uniqueUser();
        register(username, "password123");

        ResponseEntity<Void> duplicate = register(username, "password123");

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() {
        String username = uniqueUser();
        register(username, "password123");

        ResponseEntity<Map> response = loginRaw(username, "wrongpass1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("message")).isEqualTo("User or Password is invalid.");
    }

    // Anti-enumeracao: usuario inexistente e senha errada retornam a MESMA mensagem de erro.
    @Test
    void loginWithUnknownUserReturnsSameMessageAsWrongPassword() {
        String username = uniqueUser();
        register(username, "password123");

        ResponseEntity<Map> unknownUser = loginRaw("definitively_not_registered", "password123");
        ResponseEntity<Map> wrongPassword = loginRaw(username, "wrongpass1");

        assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownUser.getBody().get("message")).isEqualTo(wrongPassword.getBody().get("message"));
    }

    // Fluxo completo: login -> logout revoga o refresh token -> reuso do token falha.
    @Test
    void logoutRevokesRefreshTokenAndReuseFails() {
        String username = uniqueUser();
        register(username, "password123");
        TokenData tokens = login(username, "password123");

        ResponseEntity<Void> loggedOut = logout(tokens.accessToken(), tokens.refreshToken());
        assertThat(loggedOut.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<TokenData> replay = refresh(tokens.refreshToken());
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    // Rotacao: o refresh devolve um token NOVO e o token antigo vira invalido (single use).
    @Test
    void refreshRotatesTokenAndRevokesPrevious() {
        String username = uniqueUser();
        register(username, "password123");
        TokenData first = login(username, "password123");

        ResponseEntity<TokenData> refreshed = refresh(first.refreshToken());

        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody().refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(refreshed.getBody().accessToken()).isNotBlank();

        ResponseEntity<TokenData> replay = refresh(first.refreshToken());
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    // O access token (Bearer) sozinho ja permite chamar um endpoint autenticado.
    @Test
    void validAccessTokenGrantsAccessToProtectedEndpoint() {
        String username = uniqueUser();
        register(username, "password123");
        TokenData tokens = login(username, "password123");

        ResponseEntity<Void> response = logout(tokens.accessToken(), tokens.refreshToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // Payloads invalidos (validacao Bean Validation) retornam 400 e nao criam recursos.
    @Test
    void invalidPayloadsReturnBadRequest() {
        String username = uniqueUser();

        ResponseEntity<Void> shortPassword = register(username, "short");
        assertThat(shortPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> emptyLogin = rest.postForEntity("/authentication/login",
                new HttpEntity<>(Map.of(), jsonHeaders()),
                Map.class);
        assertThat(emptyLogin.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // O usuario com senha invalida nao deve ser criado: login nao faz sucesso.
        ResponseEntity<Map> login = loginRaw(username, "password123");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // BUG DOCUMENTADO: o admin seedado tem senha "123", abaixo do minimo de 8 caracteres
    // exigido pelo LoginRequest. Por causa disso o admin NAO consegue logar via API hoje.
    @Test
    void adminSeedCannotLoginBecauseSeedPasswordIsTooShort() {
        ResponseEntity<Map> response = loginRaw("admin", "123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // PENDENTE: esperado que o admin seedado consiga logar (corrigir a senha do seed).
    @org.junit.jupiter.api.Disabled("Pendente: corrigir senha default do admin no AdminUserConfig")
    @Test
    void adminSeedShouldBeAbleToLogin() {
        TokenData tokens = login("admin", "123");
        assertThat(tokens.accessToken()).isNotBlank();
    }

    // Invariante de seguranca: usar o MESMO refresh token concorrentemente nao pode
    // produzir duas sessoes validas. PENDENTE: hoje a implementacao nao usa nenhum tipo
    // de lock/UPDATE atomico, entao os 2 refreshes paralelos conseguem 2x 200 (ou seja,
    // existe a vulnerabilidade de session-fixation/reuse). Habilitar apos o fix.
    @org.junit.jupiter.api.Disabled("Pendente: implementar consumo atomico do refresh token (lock/UPDATE revogado = false)")
    @Test
    void concurrentUseOfSameRefreshTokenMustNotCreateTwoSessions() {
        String username = uniqueUser();
        register(username, "password123");
        TokenData tokens = login(username, "password123");

        Callable<Integer> refreshNow = () -> refresh(tokens.refreshToken()).getStatusCode().value();

        try {
            Future<Integer> first = executor.submit(refreshNow);
            Future<Integer> second = executor.submit(refreshNow);

            List<Integer> statuses = List.of(first.get(), second.get());
            long successes = statuses.stream().filter(s -> s == HttpStatus.OK.value()).count();

            // Apenas um dos refreshes paralelos pode ter sucesso (o outro deve ser rejeitado).
            assertThat(successes).isLessThanOrEqualTo(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}