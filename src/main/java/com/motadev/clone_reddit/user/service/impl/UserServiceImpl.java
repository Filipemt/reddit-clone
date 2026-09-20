package com.motadev.clone_reddit.user.service.impl;

import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.user.convert.UserConvert;
import com.motadev.clone_reddit.user.dtos.request.UserRequestDTO;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;
import com.motadev.clone_reddit.user.dtos.response.UserResponseDTO;
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
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           UserConvert userConvert,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userConvert = userConvert;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void register(UserRequestDTO userRequest) {
        var userFromDb = userRepository.findByUsername(userRequest.username());
        if (userFromDb.isPresent()) {
            throw new ResourceAlreadyExists("Resource Already Exists.");
        }

        userRepository.save(
                userConvert.convertDtoToEntity(userRequest)
        );
    }

    @Override
    public UserResponseDTO getUser(UUID userId) {
        return userConvert.convertEntityToDTo(userId);
    }

    @Override
    public Optional<UserAuthInfo> validateCredentials(String username, String rawPassword) {
        return userRepository.findByUsername(username)
                .filter(user -> user.isLoginCorrect(rawPassword, passwordEncoder))
                .map(user -> new UserAuthInfo(user.getUserId(), user.getRoleNames()));
    }

    @Override
    public Optional<UserAuthInfo> findAuthInfoById(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> new UserAuthInfo(user.getUserId(), user.getRoleNames()));
    }
}
