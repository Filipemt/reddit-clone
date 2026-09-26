package com.motadev.clone_reddit.user.service.impl;

import com.motadev.clone_reddit.auth.service.RefreshTokenServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.shared.exception.UnauthorizedException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.user.convert.UserConvert;
import com.motadev.clone_reddit.user.dtos.request.UserRequestDTO;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;
import com.motadev.clone_reddit.user.dtos.response.UserResponseDTO;
import com.motadev.clone_reddit.user.entity.Role;
import com.motadev.clone_reddit.user.entity.User;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import com.motadev.clone_reddit.user.repository.RoleRepository;
import com.motadev.clone_reddit.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
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
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RefreshTokenServiceI refreshTokenService;

    private BCryptPasswordEncoder passwordEncoder;
    private UserConvert userConvert;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        passwordEncoder = new BCryptPasswordEncoder();
        userConvert = new UserConvert(roleRepository, passwordEncoder, userRepository);
        service = new UserServiceImpl(userRepository, roleRepository, userConvert, refreshTokenService, passwordEncoder,
                new AuthenticatedUserProvider());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(UUID userId) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_BASIC")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    private User userWithRoles(UUID userId, String username, String rawPassword, Role... roles) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRoles(Set.of(roles));
        return user;
    }

    @Test
    void registerSavesUserWithHashedPasswordAndBasicRole() {
        Role basicRole = role(RoleValues.BASIC.name());
        when(roleRepository.findByName(RoleValues.BASIC.name())).thenReturn(Optional.of(basicRole));
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);

        service.register(new UserRequestDTO("bob", "password123", "bob@example.com"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("bob");
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(saved.getRoles()).isEqualTo(Set.of(basicRole));
    }

    @Test
    void registerDoesNotStorePlaintextPassword() {
        Role basicRole = role(RoleValues.BASIC.name());
        when(roleRepository.findByName(RoleValues.BASIC.name())).thenReturn(Optional.of(basicRole));
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);

        service.register(new UserRequestDTO("bob", "password123", "bob@example.com"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String stored = captor.getValue().getPassword();
        assertThat(stored).doesNotContain("password123");
        assertThat(passwordEncoder.matches("password123", stored)).isTrue();
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("bob")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new UserRequestDTO("bob", "password123", "bob@example.com")))
                .isInstanceOf(ResourceAlreadyExists.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new UserRequestDTO("bob", "password123", "bob@example.com")))
                .isInstanceOf(ResourceAlreadyExists.class);
        verify(userRepository, never()).save(any());
    }

    // Se a role BASIC nao estiver seedada no banco, o registro falha sem criar usuario.
    @Test
    void registerFailsWhenBasicRoleIsMissing() {
        when(roleRepository.findByName(RoleValues.BASIC.name())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(new UserRequestDTO("bob", "password123", "bob@example.com")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    // Registro nunca atribui ADMIN: verifica que salva somente a role BASIC.
    @Test
    void registerNeverAssignsAdminRole() {
        Role basicRole = role(RoleValues.BASIC.name());
        when(roleRepository.findByName(RoleValues.BASIC.name())).thenReturn(Optional.of(basicRole));
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);

        service.register(new UserRequestDTO("bob", "password123", "bob@example.com"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRoles())
                .extracting(Role::getName)
                .containsExactly(RoleValues.BASIC.name());
    }

    @Test
    void validateCredentialsReturnsAuthInfoWhenPasswordMatches() {
        Role basicRole = role(RoleValues.BASIC.name());
        User user = userWithRoles(UUID.nameUUIDFromBytes("alice".getBytes()), "alice", "password123", basicRole);
        when(userRepository.findByUsernameAndIsActiveTrue("alice")).thenReturn(Optional.of(user));

        Optional<UserAuthInfo> result = service.validateCredentials("alice", "password123");

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo(user.getUserId());
        assertThat(result.get().roles()).containsExactly(RoleValues.BASIC.name());
    }

    @Test
    void validateCredentialsEmptyWhenPasswordIsWrong() {
        Role basicRole = role(RoleValues.BASIC.name());
        User user = userWithRoles(UUID.randomUUID(), "alice", "password123", basicRole);
        when(userRepository.findByUsernameAndIsActiveTrue("alice")).thenReturn(Optional.of(user));

        Optional<UserAuthInfo> result = service.validateCredentials("alice", "wrongpass");

        assertThat(result).isEmpty();
    }

    @Test
    void validateCredentialsEmptyWhenUsernameIsUnknown() {
        when(userRepository.findByUsernameAndIsActiveTrue("ghost")).thenReturn(Optional.empty());

        Optional<UserAuthInfo> result = service.validateCredentials("ghost", "password123");

        assertThat(result).isEmpty();
    }

    @Test
    void findAuthInfoByIdReturnsUserRoles() {
        Role basicRole = role(RoleValues.BASIC.name());
        UUID userId = UUID.randomUUID();
        User user = userWithRoles(userId, "alice", "password123", basicRole);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Optional<UserAuthInfo> result = service.findAuthInfoById(userId);

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo(userId);
        assertThat(result.get().roles()).containsExactly(RoleValues.BASIC.name());
    }

    @Test
    void findAuthInfoByIdEmptyWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        Optional<UserAuthInfo> result = service.findAuthInfoById(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void getUserByIdReturnsDto() {
        UUID userId = UUID.randomUUID();
        User user = userWithRoles(userId, "alice", "password123", role(RoleValues.BASIC.name()));
        user.setEmail("alice@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponseDTO dto = service.getUserById(userId);

        assertThat(dto.userId()).isEqualTo(userId);
        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.email()).isEqualTo("alice@example.com");
        assertThat(dto.karma()).isZero();
    }

    @Test
    void getUserByIdThrowsWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserById(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void softDeleteMyAccountDeactivatesUserAndRevokesRefreshTokens() {
        UUID userId = UUID.randomUUID();
        User user = userWithRoles(userId, "alice", "password123", role(RoleValues.BASIC.name()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        setAuthenticatedUser(userId);

        service.softDeleteMyAccount();

        assertThat(user.isActive()).isFalse();
        verify(refreshTokenService).revokeAllByUserId(userId);
    }

    @Test
    void softDeleteMyAccountThrowsWhenNotAuthenticated() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.softDeleteMyAccount())
                .isInstanceOf(UnauthorizedException.class);
        verify(userRepository, never()).findById(any());
        verify(refreshTokenService, never()).revokeAllByUserId(any());
    }

    @Test
    void softDeleteMyAccountThrowsWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        setAuthenticatedUser(userId);

        assertThatThrownBy(() -> service.softDeleteMyAccount())
                .isInstanceOf(ResourceNotFoundException.class);
        verify(refreshTokenService, never()).revokeAllByUserId(any());
    }
}
