package com.motadev.clone_reddit.auth.dtos.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Set<ConstraintViolation<LoginRequest>> validateLogin(LoginRequest request) {
        return validator.validate(request);
    }

    private Set<ConstraintViolation<RefreshTokenRequest>> validateRefresh(RefreshTokenRequest request) {
        return validator.validate(request);
    }

    private Set<ConstraintViolation<RevokeRequest>> validateRevoke(RevokeRequest request) {
        return validator.validate(request);
    }

    @Test
    void loginRequestAcceptsValidCredentials() {
        assertThat(validateLogin(new LoginRequest("alice", "password123"))).isEmpty();
    }

    @Test
    void loginRequestRejectsBlankUsername() {
        assertThat(validateLogin(new LoginRequest("", "password123"))).isNotEmpty();
        assertThat(validateLogin(new LoginRequest("   ", "password123"))).isNotEmpty();
        assertThat(validateLogin(new LoginRequest(null, "password123"))).isNotEmpty();
    }

    @Test
    void loginRequestRejectsBlankPassword() {
        assertThat(validateLogin(new LoginRequest("alice", ""))).isNotEmpty();
        assertThat(validateLogin(new LoginRequest("alice", "   "))).isNotEmpty();
        assertThat(validateLogin(new LoginRequest("alice", null))).isNotEmpty();
    }

    @Test
    void loginRequestRejectsPasswordShorterThanEightChars() {
        assertThat(validateLogin(new LoginRequest("alice", "1234567"))).isNotEmpty();
    }

    @Test
    void loginRequestAcceptsPasswordWithExactlyEightChars() {
        assertThat(validateLogin(new LoginRequest("alice", "12345678"))).isEmpty();
    }

    @Test
    void loginRequestRejectsPasswordLongerThanSeventyTwoChars() {
        assertThat(validateLogin(new LoginRequest("alice", "x".repeat(73)))).isNotEmpty();
    }

    @Test
    void loginRequestAcceptsPasswordAtTheSeventyTwoCharLimit() {
        assertThat(validateLogin(new LoginRequest("alice", "x".repeat(72)))).isEmpty();
    }

    @Test
    void refreshTokenRequestRejectsBlank() {
        assertThat(validateRefresh(new RefreshTokenRequest(""))).isNotEmpty();
        assertThat(validateRefresh(new RefreshTokenRequest(null))).isNotEmpty();
        assertThat(validateRefresh(new RefreshTokenRequest("  "))).isNotEmpty();
    }

    @Test
    void refreshTokenRequestAcceptsValidToken() {
        assertThat(validateRefresh(new RefreshTokenRequest("uuid-token"))).isEmpty();
    }

    @Test
    void revokeRequestRejectsBlank() {
        assertThat(validateRevoke(new RevokeRequest(""))).isNotEmpty();
        assertThat(validateRevoke(new RevokeRequest(null))).isNotEmpty();
        assertThat(validateRevoke(new RevokeRequest("  "))).isNotEmpty();
    }

    @Test
    void revokeRequestAcceptsValidToken() {
        assertThat(validateRevoke(new RevokeRequest("uuid-token"))).isEmpty();
    }
}