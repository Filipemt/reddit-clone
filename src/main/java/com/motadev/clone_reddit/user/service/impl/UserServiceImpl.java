package com.motadev.clone_reddit.user.service.impl;

import com.motadev.clone_reddit.auth.service.RefreshTokenServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.shared.exception.UnauthorizedException;
import com.motadev.clone_reddit.user.convert.UserConvert;
import com.motadev.clone_reddit.user.dtos.request.UserRequestDTO;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;
import com.motadev.clone_reddit.user.dtos.response.UserResponseDTO;
import com.motadev.clone_reddit.user.entity.Role;
import com.motadev.clone_reddit.user.entity.User;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import com.motadev.clone_reddit.user.repository.RoleRepository;
import com.motadev.clone_reddit.user.repository.UserRepository;
import com.motadev.clone_reddit.user.service.UserServiceI;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class UserServiceImpl implements UserServiceI {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserConvert userConvert;
    private final RefreshTokenServiceI refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           UserConvert userConvert,
                           RefreshTokenServiceI refreshTokenService,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userConvert = userConvert;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void register(UserRequestDTO userRequest) {
        validateUniqueUser(userRequest);
        Role basicRole = roleRepository.findByName(RoleValues.BASIC.name())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found."));

        String encodedPassword = passwordEncoder.encode(userRequest.password());

        User user = userRepository.save(userConvert.convertDtoToEntity(userRequest, encodedPassword, basicRole));

        log.atInfo()
                .addKeyValue("event", "user.register.success")
                .addKeyValue("userId", user.getUserId())
                .addKeyValue("username", user.getUsername())
                .setMessage("User registered")
                .log();
    }

    @Override
    public UserResponseDTO getUserById(UUID userId) {
        User user = findUserOrThrow(userId);

        log.atInfo()
                .addKeyValue("event", "user.get.success")
                .addKeyValue("userId", userId)
                .setMessage("User fetched")
                .log();

        return userConvert.convertEntityToDto(user);
    }

    @Override
    @Transactional
    public void softDeleteMyAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            log.atWarn()
                    .addKeyValue("event", "user.account.delete.unauthorized")
                    .setMessage("Unauthenticated attempt to delete an account")
                    .log();
            throw new UnauthorizedException("User is not authenticated.");
        }

        UUID userId = UUID.fromString(auth.getName());
        User user = findUserOrThrow(userId);

        user.setActive(false);
        refreshTokenService.revokeAllByUserId(userId);

        log.atInfo()
                .addKeyValue("event", "user.account.deleted")
                .addKeyValue("userId", userId)
                .addKeyValue("username", user.getUsername())
                .setMessage("User account soft deleted")
                .log();
    }

    @Override
    public Optional<UserAuthInfo> validateCredentials(String username, String rawPassword) {
        return userRepository.findByUsernameAndIsActiveTrue(username)
                .filter(user -> user.isLoginCorrect(rawPassword, passwordEncoder))
                .map(user -> new UserAuthInfo(user.getUserId(), user.getRoleNames()));
    }

    @Override
    public Optional<UserAuthInfo> findAuthInfoById(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> new UserAuthInfo(user.getUserId(), user.getRoleNames()));
    }

    private void validateUniqueUser(UserRequestDTO userRequest) {
        if (userRepository.existsByUsername(userRequest.username())) {
            log.atWarn()
                    .addKeyValue("event", "user.register.conflict")
                    .addKeyValue("field", "username")
                    .addKeyValue("username", userRequest.username())
                    .setMessage("Attempt to register with an existing username")
                    .log();
            throw new ResourceAlreadyExists("Resource Already Exists.");
        }
        if (userRepository.existsByEmail(userRequest.email())) {
            log.atWarn()
                    .addKeyValue("event", "user.register.conflict")
                    .addKeyValue("field", "email")
                    .setMessage("Attempt to register with an existing email")
                    .log();
            throw new ResourceAlreadyExists("Resource Already Exists.");
        }
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.atWarn()
                            .addKeyValue("event", "user.not_found")
                            .addKeyValue("userId", userId)
                            .setMessage("User not found")
                            .log();
                    return new ResourceNotFoundException("User not found.");
                });
    }

}
