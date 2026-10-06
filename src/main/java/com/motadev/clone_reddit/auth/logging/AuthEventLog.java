package com.motadev.clone_reddit.auth.logging;

import com.motadev.clone_reddit.auth.service.impl.AuthenticationServiceImpl;
import com.motadev.clone_reddit.auth.service.impl.RefreshTokenServiceImpl;
import com.motadev.clone_reddit.auth.service.impl.TokenServiceImpl;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthEventLog {

    private final Logger authenticationLog = LoggerFactory.getLogger(AuthenticationServiceImpl.class);
    private final Logger refreshTokenLog = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);
    private final Logger tokenLog = LoggerFactory.getLogger(TokenServiceImpl.class);
    private final Logger authenticatedUserLog = LoggerFactory.getLogger(AuthenticatedUserProvider.class);

    public AuthEventLog() {
    }

    public void loginFailed(String username) {
        authenticationLog.atWarn()
                .addKeyValue("event", "auth.login.failed")
                .addKeyValue("username", username)
                .setMessage("Authentication failed")
                .log();
    }

    public void loginSuccess(UUID userId, String username) {
        authenticationLog.atInfo()
                .addKeyValue("event", "auth.login.success")
                .addKeyValue("userId", userId)
                .addKeyValue("username", username)
                .setMessage("User authenticated")
                .log();
    }

    public void refreshCreated(UUID userId) {
        refreshTokenLog.atDebug()
                .addKeyValue("event", "auth.refresh.created")
                .addKeyValue("userId", userId)
                .setMessage("Refresh token created")
                .log();
    }

    public void refreshFailedUserMissing(UUID userId) {
        refreshTokenLog.atWarn()
                .addKeyValue("event", "auth.refresh.failed")
                .addKeyValue("userId", userId)
                .setMessage("User for refresh token no longer exists")
                .log();
    }

    public void refreshSuccess(UUID userId) {
        refreshTokenLog.atInfo()
                .addKeyValue("event", "auth.refresh.success")
                .addKeyValue("userId", userId)
                .setMessage("Refresh token rotated")
                .log();
    }

    public void logout(UUID userId) {
        refreshTokenLog.atInfo()
                .addKeyValue("event", "auth.logout")
                .addKeyValue("userId", userId)
                .setMessage("Refresh token revoked")
                .log();
    }

    public void tokensRevoked(UUID userId) {
        refreshTokenLog.atInfo()
                .addKeyValue("event", "user.account.deleted.tokens_revoked")
                .addKeyValue("userId", userId)
                .setMessage("All refresh tokens revoked for user")
                .log();
    }

    public void refreshFailedNotFound() {
        refreshTokenLog.atWarn()
                .addKeyValue("event", "auth.refresh.failed")
                .setMessage("Refresh token not found")
                .log();
    }

    public void refreshFailedInvalidOrExpired(UUID userId) {
        refreshTokenLog.atWarn()
                .addKeyValue("event", "auth.refresh.failed")
                .addKeyValue("userId", userId)
                .setMessage("Refresh token invalid or expired")
                .log();
    }

    public void jwtGenerated(UUID userId) {
        tokenLog.atDebug()
                .addKeyValue("event", "auth.jwt.generated")
                .addKeyValue("userId", userId)
                .setMessage("JWT generated")
                .log();
    }

    public void userUnauthenticated() {
        authenticatedUserLog.atWarn()
                .addKeyValue("event", "auth.user.unauthenticated")
                .setMessage("Attempt to access a protected operation without authentication")
                .log();
    }
}
