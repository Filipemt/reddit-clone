package com.motadev.clone_reddit.user.service.impl;

import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.user.convert.UserConvert;
import com.motadev.clone_reddit.user.dtos.request.UserRequestDTO;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;
import com.motadev.clone_reddit.user.entity.Role;
import com.motadev.clone_reddit.user.entity.User;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import com.motadev.clone_reddit.user.repository.RoleRepository;
import com.motadev.clone_reddit.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;

    private BCryptPasswordEncoder passwordEncoder;
    private UserConvert userConvert;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userConvert = new UserConvert(roleRepository, passwordEncoder);
        service = new UserServiceImpl(userRepository, roleRepository, userConvert, passwordEncoder);
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
        when(userRepository.findByUsernameAndIsActiveTrue("bob")).thenReturn(Optional.empty());

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
        when(userRepository.findByUsernameAndIsActiveTrue("bob")).thenReturn(Optional.empty());

        service.register(new UserRequestDTO("bob", "password123", "bob@example.com"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String stored = captor.getValue().getPassword();
        assertThat(stored).doesNotContain("password123");
        assertThat(passwordEncoder.matches("password123", stored)).isTrue();
    }

    @Test
    void registerRejectsDuplicateUsername() {
        Role basicRole = role(RoleValues.BASIC.name());
        User existing = userWithRoles(UUID.randomUUID(), "bob", "password123", basicRole);
        when(userRepository.findByUsernameAndIsActiveTrue("bob")).thenReturn(Optional.of(existing));

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
        when(userRepository.findByUsernameAndIsActiveTrue("bob")).thenReturn(Optional.empty());

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
}