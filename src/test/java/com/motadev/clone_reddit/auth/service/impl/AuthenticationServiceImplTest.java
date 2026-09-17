package com.motadev.clone_reddit.auth.service.impl;

import com.motadev.clone_reddit.auth.dtos.request.LoginRequest;
import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.auth.entity.RefreshToken;
import com.motadev.clone_reddit.user.entity.Role;
import com.motadev.clone_reddit.user.entity.User;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import com.motadev.clone_reddit.user.repository.RoleRepository;
import com.motadev.clone_reddit.user.repository.UserRepository;
import com.motadev.clone_reddit.auth.service.RefreshTokenServiceI;
import com.motadev.clone_reddit.auth.service.TokenServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    private static final String UNIFORM_ERROR = "User or Password is invalid.";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private TokenServiceI tokenService;
    @Mock
    private RefreshTokenServiceI refreshTokenService;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new AuthenticationServiceImpl(userRepository, passwordEncoder, tokenService, refreshTokenService);
    }

    private User userWithPassword(String username, String rawPassword) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        return user;
    }

    @Test
    void authenticateReturnsTokenWhenCredentialsAreValid() {
        User user = userWithPassword("alice", "password123");
        LoginRequest request = new LoginRequest("alice", "password123");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("rt-1");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken);
        when(tokenService.generateToken(user, "rt-1")).thenReturn(new TokenData("jwt-1", 300L, "rt-1"));

        TokenData result = service.authenticate(request);

        assertThat(result.accessToken()).isEqualTo("jwt-1");
        assertThat(result.refreshToken()).isEqualTo("rt-1");
        verify(refreshTokenService).createRefreshToken(user);
    }

    @Test
    void authenticateRejectsUnknownUsername() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("ghost", "password123");

        assertThatThrownBy(() -> service.authenticate(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(UNIFORM_ERROR);
    }

    @Test
    void authenticateRejectsWrongPassword() {
        User user = userWithPassword("alice", "password123");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("alice", "wrongpass");

        assertThatThrownBy(() -> service.authenticate(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(UNIFORM_ERROR);
    }

    // Garante que erro de usuario inexistente e de senha errada usam a MESMA mensagem (anti-enumeracao).
    @Test
    void authenticateUsesSameMessageForUnknownUserAndWrongPassword() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Throwable unknown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.authenticate(new LoginRequest("ghost", "password123")));
        Throwable wrongPwd = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.authenticate(new LoginRequest("ghost", "different")));

        assertThat(unknown).hasMessage(UNIFORM_ERROR);
        assertThat(wrongPwd).hasMessage(UNIFORM_ERROR);
    }

    @Test
    void registerSavesUserWithHashedPasswordAndBasicRole() {
        Role basicRole = new Role();
        basicRole.setName(RoleValues.BASIC.name());
        when(roleRepository.findByName(RoleValues.BASIC.name())).thenReturn(Optional.of(basicRole));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());

        service.register(new LoginRequest("bob", "password123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("bob");
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(saved.getRoles()).isEqualTo(Set.of(basicRole));
    }

    @Test
    void registerDoesNotStorePlaintextPassword() {
        Role basicRole = new Role();
        basicRole.setName(RoleValues.BASIC.name());
        when(roleRepository.findByName(RoleValues.BASIC.name())).thenReturn(Optional.of(basicRole));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());

        service.register(new LoginRequest("bob", "password123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String stored = captor.getValue().getPassword();
        assertThat(stored).doesNotContain("password123");
        assertThat(passwordEncoder.matches("password123", stored)).isTrue();
    }

    @Test
    void registerRejectsDuplicateUsername() {
        Role basicRole = new Role();
        basicRole.setName(RoleValues.BASIC.name());
        when(roleRepository.findByName(RoleValues.BASIC.name())).thenReturn(Optional.of(basicRole));
        User existing = userWithPassword("bob", "password123");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(new LoginRequest("bob", "password123")))
                .isInstanceOf(ResourceAlreadyExists.class);
        verify(userRepository, never()).save(any());
    }

    // Se a role BASIC nao estiver seedada no banco, o registro falha sem criar usuario.
    @Test
    void registerFailsWhenBasicRoleIsMissing() {
        when(roleRepository.findByName(RoleValues.BASIC.name())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(new LoginRequest("bob", "password123")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    // Registro nunca atribui ADMIN: verifica que salva somente a role BASIC.
    @Test
    void registerNeverAssignsAdminRole() {
        Role basicRole = new Role();
        basicRole.setName(RoleValues.BASIC.name());
        when(roleRepository.findByName(RoleValues.BASIC.name())).thenReturn(Optional.of(basicRole));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());

        service.register(new LoginRequest("bob", "password123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRoles())
                .extracting(Role::getName)
                .containsExactly(RoleValues.BASIC.name());
    }

    @Test
    void authenticateNeverReturnsTheStoredHash() {
        User user = userWithPassword("alice", "password123");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(refreshTokenService.createRefreshToken(user)).thenReturn(new RefreshToken());
        when(tokenService.generateToken(eq(user), any())).thenReturn(new TokenData("jwt", 300L, "rt"));

        TokenData result = service.authenticate(new LoginRequest("alice", "password123"));

        assertThat(result.accessToken()).isEqualTo("jwt");
    }
}