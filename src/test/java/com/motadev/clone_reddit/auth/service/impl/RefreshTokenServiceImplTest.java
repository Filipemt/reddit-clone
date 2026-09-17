package com.motadev.clone_reddit.auth.service.impl;

import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.auth.entity.RefreshToken;
import com.motadev.clone_reddit.user.entity.User;
import com.motadev.clone_reddit.auth.repository.RefreshTokenRepository;
import com.motadev.clone_reddit.auth.service.TokenServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
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
    private RefreshTokenRepository refreshRepository;
    @Mock
    private TokenServiceI tokenService;

    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenServiceImpl(refreshRepository, tokenService);
        ReflectionTestUtils.setField(service, "refreshExpirationMs", REFRESH_EXPIRATION_MS);
        // Retorna a propria entidade que foi salva, simulando o JPA que atribui o id.
        lenient().when(refreshRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private User user() {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setUsername("alice");
        return user;
    }

    private RefreshToken validToken(String value, User user) {
        RefreshToken token = new RefreshToken();
        token.setRefreshTokenId(UUID.randomUUID());
        token.setToken(value);
        token.setUser(user);
        token.setExpiryDate(Instant.now().plus(1, ChronoUnit.HOURS));
        return token;
    }

    @Test
    void createRefreshTokenStoresUserAndFutureExpiry() {
        User user = user();
        Instant before = Instant.now();

        RefreshToken token = service.createRefreshToken(user);
        Instant after = Instant.now();

        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getUser()).isEqualTo(user);
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getExpiryDate()).isBetween(before.plusMillis(REFRESH_EXPIRATION_MS - 1000),
                after.plusMillis(REFRESH_EXPIRATION_MS + 1000));
        verify(refreshRepository).save(token);
    }

    @Test
    void createRefreshTokenGeneratesUniqueUuidPerCall() {
        RefreshToken first = service.createRefreshToken(user());
        RefreshToken second = service.createRefreshToken(user());

        assertThat(first.getToken()).isNotEqualTo(second.getToken());
        assertThat(UUID.fromString(first.getToken())).isNotNull();
    }

    @Test
    void refreshRotatesToken() {
        User user = user();
        RefreshToken oldToken = validToken("rt-old", user);
        RefreshToken newToken = validToken("rt-new", user);
        when(refreshRepository.findByToken("rt-old")).thenReturn(Optional.of(oldToken));
        when(refreshRepository.save(any(RefreshToken.class))).thenReturn(newToken);
        when(tokenService.generateToken(user, "rt-new"))
                .thenReturn(new TokenData("jwt-new", 300L, "rt-new"));

        TokenData result = service.refresh("rt-old");

        assertThat(result.refreshToken()).isEqualTo("rt-new");
        assertThat(result.accessToken()).isEqualTo("jwt-new");
        assertThat(oldToken.isRevoked()).isTrue();
        verify(tokenService).generateToken(user, "rt-new");
    }

    @Test
    void refreshNeverReturnsTheSameRefreshToken() {
        User user = user();
        RefreshToken oldToken = validToken("rt-old", user);
        RefreshToken newToken = validToken("rt-new", user);
        when(refreshRepository.findByToken("rt-old")).thenReturn(Optional.of(oldToken));
        when(refreshRepository.save(any(RefreshToken.class))).thenReturn(newToken);
        when(tokenService.generateToken(user, "rt-new"))
                .thenReturn(new TokenData("jwt", 300L, "rt-new"));

        TokenData result = service.refresh("rt-old");

        assertThat(result.refreshToken()).isEqualTo("rt-new");
        assertThat(result.refreshToken()).isNotEqualTo("rt-old");
    }

    // Replay de refresh token ja revogado deve ser recusado (single-use).
    @Test
    void refreshRejectsRevokedToken() {
        User user = user();
        RefreshToken revoked = validToken("rt-revoked", user);
        revoked.setRevoked(true);
        when(refreshRepository.findByToken("rt-revoked")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.refresh("rt-revoked"))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessage("Refresh token invalid or expired");
        verify(tokenService, never()).generateToken(any(), any());
    }

    @Test
    void refreshRejectsExpiredToken() {
        User user = user();
        RefreshToken expired = validToken("rt-expired", user);
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

    @Test
    void verifyAcceptsValidToken() {
        User user = user();
        RefreshToken token = validToken("rt-valid", user);
        when(refreshRepository.findByToken("rt-valid")).thenReturn(Optional.of(token));

        RefreshToken result = service.verify("rt-valid");

        assertThat(result.getToken()).isEqualTo("rt-valid");
    }

    @Test
    void revokeMarksTokenAsRevoked() {
        User user = user();
        RefreshToken token = validToken("rt-to-revoke", user);
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
        User user = user();

        RefreshToken token = service.createRefreshToken(user);

        long ttl = token.getExpiryDate().toEpochMilli() - Instant.now().toEpochMilli();
        assertThat(ttl).isBetween(REFRESH_EXPIRATION_MS - 2000, REFRESH_EXPIRATION_MS + 2000);
    }
}