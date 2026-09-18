package com.motadev.clone_reddit.auth.service.impl;

import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.motadev.clone_reddit.support.TestJwtBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.ParseException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceImplTest {

    private static final Long EXPIRES_IN = 300L;
    private static final String ISSUER = "backend-reddit-clone";

    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;
    private TokenServiceImpl service;

    @BeforeEach
    void setUp() {
        var jwk = new com.nimbusds.jose.jwk.RSAKey.Builder(TestJwtBuilder.loadPublicKey())
                .privateKey(TestJwtBuilder.loadPrivateKey())
                .build();
        jwtEncoder = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(
                new com.nimbusds.jose.jwk.JWKSet(jwk)));
        jwtDecoder = NimbusJwtDecoder.withPublicKey(TestJwtBuilder.loadPublicKey()).build();
        service = new TokenServiceImpl(jwtEncoder);
        ReflectionTestUtils.setField(service, "expiresIn", EXPIRES_IN);
        ReflectionTestUtils.setField(service, "issuer", ISSUER);
    }

    private UserAuthInfo authInfo(String... roles) {
        return new UserAuthInfo(UUID.randomUUID(), Set.of(roles));
    }

    private JWTClaimsSet claimsOf(String token) throws ParseException {
        return SignedJWT.parse(token).getJWTClaimsSet();
    }

    @Test
    void generateTokenProducesValidSignature() {
        UserAuthInfo authInfo = authInfo("BASIC");

        String token = service.generateToken(authInfo, "rt-1").accessToken();

        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(authInfo.userId().toString());
    }

    @Test
    void generateTokenSetsIssuer() throws ParseException {
        UserAuthInfo authInfo = authInfo("BASIC");

        String token = service.generateToken(authInfo, "rt-1").accessToken();

        assertThat(claimsOf(token).getIssuer()).isEqualTo(ISSUER);
    }

    @Test
    void generateTokenUsesUserIdAsSubject() throws ParseException {
        UserAuthInfo authInfo = authInfo("BASIC");

        String token = service.generateToken(authInfo, "rt-1").accessToken();

        assertThat(claimsOf(token).getSubject()).isEqualTo(authInfo.userId().toString());
    }

    @Test
    void generateTokenSetsScopeFromSingleRole() throws ParseException {
        UserAuthInfo authInfo = authInfo("BASIC");

        String token = service.generateToken(authInfo, "rt-1").accessToken();

        assertThat(claimsOf(token).getStringClaim("scope")).isEqualTo("BASIC");
    }

    @Test
    void generateTokenSetsScopeFromMultipleRoles() throws ParseException {
        UserAuthInfo authInfo = authInfo("BASIC", "ADMIN");

        String token = service.generateToken(authInfo, "rt-1").accessToken();

        String[] scope = claimsOf(token).getStringClaim("scope").split(" ");
        assertThat(scope).containsExactlyInAnyOrder("BASIC", "ADMIN");
    }

    @Test
    void generateTokenHandlesUserWithoutRoles() throws ParseException {
        UserAuthInfo authInfo = authInfo();

        String token = service.generateToken(authInfo, "rt-1").accessToken();

        assertThat(claimsOf(token).getStringClaim("scope")).isEqualTo("");
    }

    // O exp deve respeitar jwt.expiresIn (300s). JWT grava timestamps em segundos, entao usa tolerancia de 1s.
    @Test
    void generateTokenExpirationMatchesConfiguredTtl() throws ParseException {
        UserAuthInfo authInfo = authInfo("BASIC");
        Instant before = Instant.now();

        TokenData data = service.generateToken(authInfo, "rt-1");
        Instant after = Instant.now();

        var claims = claimsOf(data.accessToken());
        assertThat(data.expiresIn()).isEqualTo(EXPIRES_IN);
        assertThat(claims.getExpirationTime().toInstant())
                .isEqualTo(claims.getIssueTime().toInstant().plusSeconds(EXPIRES_IN));
        assertThat(claims.getIssueTime().toInstant()).isBetween(before.minusSeconds(1), after);
    }

    @Test
    void generateTokenReflectsRefreshTokenInResponse() {
        UserAuthInfo authInfo = authInfo("BASIC");

        TokenData data = service.generateToken(authInfo, "rt-xyz");

        assertThat(data.refreshToken()).isEqualTo("rt-xyz");
    }

    // GAP DE SEGURANCA documentado: sem claim jti/aleatorio, dois tokens emitidos no MESMO segundo
    // para o mesmo usuario sao byte-a-byte identicos. Isso impossibilita revogar um access token individual.
    @Test
    void generateTokenProducesIdenticalTokensWithinSameSecond() {
        UserAuthInfo authInfo = authInfo("BASIC");

        String token1 = service.generateToken(authInfo, "rt-1").accessToken();
        String token2 = service.generateToken(authInfo, "rt-2").accessToken();

        assertThat(token1).isEqualTo(token2);
    }

    // PENDENTE: esperado que cada emissao produza um token unico (adicionar jti) para permitir
    // revogacao individual e deteccao de replay. Hoje falha caso habilitado.
    @org.junit.jupiter.api.Disabled("Pendente: implementar jti no TokenServiceImpl")
    @Test
    void generateTokenProducesUniqueTokensPerCall() {
        UserAuthInfo authInfo = authInfo("BASIC");

        String token1 = service.generateToken(authInfo, "rt-1").accessToken();
        String token2 = service.generateToken(authInfo, "rt-2").accessToken();

        assertThat(token1).isNotEqualTo(token2);
    }
}