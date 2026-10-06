package com.motadev.clone_reddit.user.service.impl;

import com.motadev.clone_reddit.auth.service.RefreshTokenServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.shared.security.AuthenticatedUserProvider;
import com.motadev.clone_reddit.user.convert.UserConvert;
import com.motadev.clone_reddit.user.dtos.request.UserRequestDTO;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;
import com.motadev.clone_reddit.user.dtos.response.UserResponseDTO;
import com.motadev.clone_reddit.user.entity.Role;
import com.motadev.clone_reddit.user.entity.User;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import com.motadev.clone_reddit.user.logging.UserEventLog;
import com.motadev.clone_reddit.user.repository.RoleRepository;
import com.motadev.clone_reddit.user.repository.UserRepository;
import com.motadev.clone_reddit.user.service.UserServiceI;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserServiceI {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserConvert userConvert;
    private final RefreshTokenServiceI refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final UserEventLog userEventLog;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           UserConvert userConvert,
                           RefreshTokenServiceI refreshTokenService,
                           PasswordEncoder passwordEncoder,
                           AuthenticatedUserProvider authenticatedUserProvider,
                           UserEventLog userEventLog) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userConvert = userConvert;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.userEventLog = userEventLog;
    }

    @Override
    @Transactional
    public void register(UserRequestDTO userRequest) {
        validateUniqueUser(userRequest);
        Role basicRole = roleRepository.findByName(RoleValues.BASIC.name())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found."));

        String encodedPassword = passwordEncoder.encode(userRequest.password());

        User user = userRepository.save(userConvert.convertDtoToEntity(userRequest, encodedPassword, basicRole));

        userEventLog.registerSuccess(user.getUserId(), user.getUsername());
    }

    @Override
    public UserResponseDTO getUserById(UUID userId) {
        User user = findUserOrThrow(userId);

        userEventLog.getSuccess(userId);

        return userConvert.convertEntityToDto(user);
    }

    @Override
    @Transactional
    public void softDeleteMyAccount() {
        UUID userId = authenticatedUserProvider.extractUserIdFromAuthentication();
        User user = findUserOrThrow(userId);

        user.setActive(false);
        refreshTokenService.revokeAllByUserId(userId);

        userEventLog.accountDeleted(userId, user.getUsername());
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
            userEventLog.registerConflictUsername(userRequest.username());
            throw new ResourceAlreadyExists("Resource Already Exists.");
        }
        if (userRepository.existsByEmail(userRequest.email())) {
            userEventLog.registerConflictEmail();
            throw new ResourceAlreadyExists("Resource Already Exists.");
        }
    }

    private User findUserOrThrow(UUID userId) {
        // Todo: Adicionar consulta para buscar usuários ativos
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    userEventLog.notFound(userId);
                    return new ResourceNotFoundException("User not found.");
                });
    }

}
