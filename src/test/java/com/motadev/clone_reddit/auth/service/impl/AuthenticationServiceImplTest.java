package com.motadev.clone_reddit.auth.service.impl;

import com.motadev.clone_reddit.auth.dtos.request.LoginRequest;
import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.auth.entity.RefreshToken;
import com.motadev.clone_reddit.auth.service.RefreshTokenServiceI;
import com.motadev.clone_reddit.auth.service.TokenServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;
import com.motadev.clone_reddit.user.service.UserServiceI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    private static final String UNIFORM_ERROR = "User or Password Invalid.";

    @Mock
    private UserServiceI userService;
    @Mock
    private TokenServiceI tokenService;
    @Mock
    private RefreshTokenServiceI refreshTokenService;

    private AuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationServiceImpl(userService, tokenService, refreshTokenService);
    }

    private UserAuthInfo authInfo() {
        return new UserAuthInfo(UUID.randomUUID(), Set.of("BASIC"));
    }

    @Test
    void authenticateReturnsTokenWhenCredentialsAreValid() {
        UserAuthInfo authInfo = authInfo();
        LoginRequest request = new LoginRequest("alice", "password123");
        when(userService.validateCredentials("alice", "password123")).thenReturn(Optional.of(authInfo));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("rt-1");
        when(refreshTokenService.createRefreshToken(authInfo)).thenReturn(refreshToken);
        when(tokenService.generateToken(authInfo, "rt-1")).thenReturn(new TokenData("jwt-1", 300L, "rt-1"));

        TokenData result = service.authenticate(request);

        assertThat(result.accessToken()).isEqualTo("jwt-1");
        assertThat(result.refreshToken()).isEqualTo("rt-1");
        verify(refreshTokenService).createRefreshToken(authInfo);
    }

    @Test
    void authenticateRejectsUnknownUsername() {
        when(userService.validateCredentials("ghost", "password123")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("ghost", "password123");

        assertThatThrownBy(() -> service.authenticate(request))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessage(UNIFORM_ERROR);
    }

    @Test
    void authenticateRejectsWrongPassword() {
        when(userService.validateCredentials("alice", "wrongpass")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("alice", "wrongpass");

        assertThatThrownBy(() -> service.authenticate(request))
                .isInstanceOf(ResourceInvalidException.class)
                .hasMessage(UNIFORM_ERROR);
    }

    // Garante que erro de usuario inexistente e de senha errada usam a MESMA mensagem (anti-enumeracao).
    @Test
    void authenticateUsesSameMessageForUnknownUserAndWrongPassword() {
        when(userService.validateCredentials("ghost", "password123")).thenReturn(Optional.empty());
        when(userService.validateCredentials("ghost", "different")).thenReturn(Optional.empty());

        Throwable unknown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.authenticate(new LoginRequest("ghost", "password123")));
        Throwable wrongPwd = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.authenticate(new LoginRequest("ghost", "different")));

        assertThat(unknown).hasMessage(UNIFORM_ERROR);
        assertThat(wrongPwd).hasMessage(UNIFORM_ERROR);
    }

    // Credenciais invalidas nunca emitem refresh ou access token.
    @Test
    void authenticateDoesNotGenerateTokensWhenCredentialsAreInvalid() {
        when(userService.validateCredentials("ghost", "password123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("ghost", "password123")))
                .isInstanceOf(ResourceInvalidException.class);

        verify(refreshTokenService, never()).createRefreshToken(any());
        verify(tokenService, never()).generateToken(any(), any());
    }

    // A response nunca expoe a entidade User nem o hash: gera-se o token apenas a partir do UserAuthInfo.
    @Test
    void authenticateForwardsCreatedRefreshTokenToTokenGenerator() {
        UserAuthInfo authInfo = authInfo();
        when(userService.validateCredentials("alice", "password123")).thenReturn(Optional.of(authInfo));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("rt-42");
        when(refreshTokenService.createRefreshToken(authInfo)).thenReturn(refreshToken);
        when(tokenService.generateToken(authInfo, "rt-42"))
                .thenReturn(new TokenData("jwt", 300L, "rt-42"));

        TokenData result = service.authenticate(new LoginRequest("alice", "password123"));

        assertThat(result.accessToken()).isEqualTo("jwt");
        assertThat(result.refreshToken()).isEqualTo("rt-42");
        verify(tokenService).generateToken(authInfo, "rt-42");
    }
}