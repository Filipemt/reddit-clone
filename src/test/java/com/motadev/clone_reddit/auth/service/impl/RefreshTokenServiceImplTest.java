package com.motadev.clone_reddit.auth.service.impl;

import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.auth.entity.RefreshToken;
import com.motadev.clone_reddit.auth.repository.RefreshTokenRepository;
import com.motadev.clone_reddit.auth.service.TokenServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;
import com.motadev.clone_reddit.user.service.UserServiceI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final long REFRESH_EXPIRATION_MS = 86_400_000L;

    @Mock
    private UserServiceI userService;
    @Mock
    private RefreshTokenRepository refreshRepository;
    @Mock
    private TokenServiceI tokenService;

    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenServiceImpl(userService, refreshRepository, tokenService);
        ReflectionTestUtils.setField(service, "refreshExpirationMs", REFRESH_EXPIRATION_MS);
        // Retorna a propria entidade que foi salva, simulando o JPA que atribui o id.
        lenient().when(refreshRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private UserAuthInfo authInfo() {
        return new UserAuthInfo(UUID.randomUUID(), Set.of("BASIC"));
    }

    private RefreshToken validToken(String value, UUID userId) {
        RefreshToken token = new RefreshToken();
        token.setRefreshTokenId(UUID.randomUUID());
        token.setToken(value);
        token.setUserId(userId);
        token.setExpiryDate(Instant.now().plus(1, ChronoUnit.HOURS));
        return token;
    }

    @Test
    void createRefreshTokenStoresUserAndFutureExpiry() {
        UserAuthInfo authInfo = authInfo();
        Instant before = Instant.now();

        RefreshToken token = service.createRefreshToken(authInfo);
        Instant after = Instant.now();

        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getUserId()).isEqualTo(authInfo.userId());
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getExpiryDate()).isBetween(before.plusMillis(REFRESH_EXPIRATION_MS - 1000),
                after.plusMillis(REFRESH_EXPIRATION_MS + 1000));
        verify(refreshRepository).save(token);
    }

    @Test
    void createRefreshTokenGeneratesUniqueUuidPerCall() {
        RefreshToken first = service.createRefreshToken(authInfo());
        RefreshToken second = service.createRefreshToken(authInfo());

        assertThat(first.getToken()).isNotEqualTo(second.getToken());
        assertThat(UUID.fromString(first.getToken())).isNotNull();
    }

    @Test
    void refreshRotatesToken() {
        UserAuthInfo authInfo = authInfo();
        RefreshToken oldToken = validToken("rt-old", authInfo.userId());
        RefreshToken newToken = validToken("rt-new", authInfo.userId());
        when(refreshRepository.findByToken("rt-old")).thenReturn(Optional.of(oldToken));
        when(userService.findAuthInfoById(authInfo.userId())).thenReturn(Optional.of(authInfo));
        when(refreshRepository.save(any(RefreshToken.class))).thenReturn(newToken);
        when(tokenService.generateToken(authInfo, "rt-new"))
                .thenReturn(new TokenData("jwt-new", 300L, "rt-new"));

        TokenData result = service.refresh("rt-old");

        assertThat(result.refreshToken()).isEqualTo("rt-new");
        assertThat(result.accessToken()).isEqualTo("jwt-new");
        assertThat(oldToken.isRevoked()).isTrue();
        verify(tokenService).generateToken(authInfo, "rt-new");
    }

    @Test
    void refreshNeverReturnsTheSameRefreshToken() {
        UserAuthInfo authInfo = authInfo();
        RefreshToken oldToken = validToken("rt-old", authInfo.userId());
        RefreshToken newToken = validToken("rt-new", authInfo.userId());
        when(refreshRepository.findByToken("rt-old")).thenReturn(Optional.of(oldToken));
        when(userService.findAuthInfoById(authInfo.userId())).thenReturn(Optional.of(authInfo));
        when(refreshRepository.save(any(RefreshToken.class))).thenReturn(newToken);
        when(tokenService.generateToken(authInfo, "rt-new"))
                .thenReturn(new TokenData("jwt", 300L, "rt-new"));

        TokenData result = service.refresh("rt-old");

        assertThat(result.refreshToken()).isEqualTo("rt-new");
        assertThat(result.refreshToken()).isNotEqualTo("rt-old");
    }

    // Replay de refresh token ja revogado deve ser recusado (single-use).
    @Test
    void refreshRejectsRevokedToken() {
        UserAuthInfo authInfo = authInfo();
        RefreshToken revoked = validToken("rt-revoked", authInfo.userId());
        revoked.setRevoked(true);
        when(refreshRepository.findByToken("rt-revoked")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.refresh("rt-revoked"))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessage("Refresh token invalid or expired");
        verify(tokenService, never()).generateToken(any(), any());
    }

    @Test
    void refreshRejectsExpiredToken() {
        UserAuthInfo authInfo = authInfo();
        RefreshToken expired = validToken("rt-expired", authInfo.userId());
        expired.setExpiryDate(Instant.now().minus(1, ChronoUnit.HOURS));
        when(refreshRepository.findByToken("rt-expired")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.refresh("rt-expired"))
                .isInstanceOf(ResourceInvalidException.class);
        verify(tokenService, never()).generateToken(any(), any());
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(refreshRepository.findByToken("rt-nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("rt-nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Usuario excluido depois da emissao do refresh token impede a renovacao.
    @Test
    void refreshFailsWhenUserNoLongerExists() {
        UserAuthInfo authInfo = authInfo();
        RefreshToken oldToken = validToken("rt-old", authInfo.userId());
        when(refreshRepository.findByToken("rt-old")).thenReturn(Optional.of(oldToken));
        when(userService.findAuthInfoById(authInfo.userId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("rt-old"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(tokenService, never()).generateToken(any(), any());
    }

    @Test
    void verifyAcceptsValidToken() {
        UserAuthInfo authInfo = authInfo();
        RefreshToken token = validToken("rt-valid", authInfo.userId());
        when(refreshRepository.findByToken("rt-valid")).thenReturn(Optional.of(token));

        RefreshToken result = service.verify("rt-valid");

        assertThat(result.getToken()).isEqualTo("rt-valid");
    }

    @Test
    void revokeMarksTokenAsRevoked() {
        UserAuthInfo authInfo = authInfo();
        RefreshToken token = validToken("rt-to-revoke", authInfo.userId());
        when(refreshRepository.findByToken("rt-to-revoke")).thenReturn(Optional.of(token));

        service.revoke("rt-to-revoke");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshRepository).save(token);
    }

    // Revogar token inexistente e uma operacao segura (nao gera erro).
    @Test
    void revokeIsIdempotentForUnknownToken() {
        when(refreshRepository.findByToken("rt-nope")).thenReturn(Optional.empty());

        service.revoke("rt-nope");

        verify(refreshRepository, never()).save(any(RefreshToken.class));
    }

    // Expira em exatamente refreshExpirationMs (24h) a partir da criacao.
    @Test
    void createRefreshTokenExpiryMatchesConfiguredTtl() {
        UserAuthInfo authInfo = authInfo();

        RefreshToken token = service.createRefreshToken(authInfo);

        long ttl = token.getExpiryDate().toEpochMilli() - Instant.now().toEpochMilli();
        assertThat(ttl).isBetween(REFRESH_EXPIRATION_MS - 2000, REFRESH_EXPIRATION_MS + 2000);
    }
}