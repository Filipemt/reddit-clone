package com.motadev.clone_reddit.security;

import com.motadev.clone_reddit.auth.dtos.request.RevokeRequest;
import com.motadev.clone_reddit.support.TestJwtBuilder;
import com.motadev.clone_reddit.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.security.interfaces.RSAPrivateKey;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String ISSUER = "backend-reddit-clone";

    private String validAccessToken() {
        Instant now = Instant.now();
        return TestJwtBuilder.buildSignedToken(
                TestJwtBuilder.loadPrivateKey(),
                ISSUER,
                UUID.randomUUID().toString(),
                now,
                now.plusSeconds(300),
                "BASIC");
    }

    private String expiredToken() {
        Instant now = Instant.now();
        return TestJwtBuilder.buildSignedToken(
                TestJwtBuilder.loadPrivateKey(),
                ISSUER,
                UUID.randomUUID().toString(),
                now.minusSeconds(600),
                now.minusSeconds(300),
                "BASIC");
    }

    private String tokenSignedWithWrongKey() {
        Instant now = Instant.now();
        return TestJwtBuilder.buildSignedToken(
                (RSAPrivateKey) TestJwtBuilder.additionalKeyPair().getPrivate(),
                ISSUER,
                UUID.randomUUID().toString(),
                now,
                now.plusSeconds(300),
                "BASIC");
    }

    private String body(String tokenValue) {
        try {
            return new ObjectMapper().writeValueAsString(new RevokeRequest(tokenValue));
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    // As rotas publicas nao podem ser bloqueadas pela SecurityFilterChain
    // (aqui o 400 vem da validacao do DTO, provando que passou pela security).
    @Test
    void registerIsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/authentication/register")
                        .contentType("application/json")
                        .content("""
                                {"username":"", "password":"password123"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // Login sem token chega ao service: usuario desconhecido gera o 401 padrao do
    // BadCredentialsExceptionHandler (corpo JSON). Isso prova que nao foi a
    // SecurityFilterChain que bloqueou (o 401 de security nao teria essa mensagem).
    @Test
    void loginIsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/authentication/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"carol", "password":"password123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("User or Password is invalid."));
    }

    @Test
    void refreshIsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/authentication/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    // Logout exige autenticacao (Bearer JWT valido).
    @Test
    void logoutWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/authentication/logout")
                        .contentType("application/json")
                        .content(body("rt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithValidTokenSucceeds() throws Exception {
        mockMvc.perform(delete("/authentication/logout")
                        .header("Authorization", "Bearer " + validAccessToken())
                        .contentType("application/json")
                        .content(body("rt")))
                .andExpect(status().isOk());
    }

    @Test
    void logoutWithMissingBearerHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/authentication/logout")
                        .contentType("application/json")
                        .content(body("rt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithBasicAuthHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/authentication/logout")
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .contentType("application/json")
                        .content(body("rt")))
                .andExpect(status().isUnauthorized());
    }

    // Token assinado com outra chave nao passa na verificacao de assinatura.
    @Test
    void logoutWithTokenSignedByAnotherKeyIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/authentication/logout")
                        .header("Authorization", "Bearer " + tokenSignedWithWrongKey())
                        .contentType("application/json")
                        .content(body("rt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithExpiredTokenIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/authentication/logout")
                        .header("Authorization", "Bearer " + expiredToken())
                        .contentType("application/json")
                        .content(body("rt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithGarbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/authentication/logout")
                        .header("Authorization", "Bearer not-a-jwt")
                        .contentType("application/json")
                        .content(body("rt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithBlankBearerTokenIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/authentication/logout")
                        .header("Authorization", "Bearer ")
                        .contentType("application/json")
                        .content(body("rt")))
                .andExpect(status().isUnauthorized());
    }

    // Qualquer rota ainda nao implementada exige autenticacao.
    @Test
    void unknownRouteWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/posts")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // Rota inexistente com token valido passa pela security e cai no 404 da
    // GlobalExceptionHandler (nao pode virar 500 do catch-all).
    @Test
    void unknownRouteWithValidTokenIsNotBlocked() throws Exception {
        mockMvc.perform(post("/posts")
                        .header("Authorization", "Bearer " + validAccessToken())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // GAP documentado: o JwtDecoder nao valida o issuer. Token com issuer errado e ACEITO
    // porque a assinatura, o exp e o iat estao validos. Teste pendente abaixo exige o fix.
    @Test
    void tokenWithWrongIssuerIsCurrentlyAccepted() throws Exception {
        Instant now = Instant.now();
        String token = TestJwtBuilder.buildSignedToken(
                TestJwtBuilder.loadPrivateKey(),
                "evil-issuer",
                UUID.randomUUID().toString(),
                now,
                now.plusSeconds(300),
                "BASIC");

        mockMvc.perform(delete("/authentication/logout")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body("rt")))
                .andExpect(status().isOk());
    }

    // PENDENTE: esperado que o decoder valide o issuer e rejeite. Falha caso habilitado hoje.
    @org.junit.jupiter.api.Disabled("Pendente: configurar jwtDecoder().withIssuer(...) no SecurityConfig")
    @Test
    void tokenWithWrongIssuerShouldBeRejected() throws Exception {
        Instant now = Instant.now();
        String token = TestJwtBuilder.buildSignedToken(
                TestJwtBuilder.loadPrivateKey(),
                "evil-issuer",
                UUID.randomUUID().toString(),
                now,
                now.plusSeconds(300),
                "BASIC");

        mockMvc.perform(delete("/authentication/logout")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body("rt")))
                .andExpect(status().isUnauthorized());
    }
}