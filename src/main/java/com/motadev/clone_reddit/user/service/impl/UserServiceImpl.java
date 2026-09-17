package com.motadev.clone_reddit.user.service.impl;

import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.user.dtos.request.UserRequestDTO;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;
import com.motadev.clone_reddit.user.entity.User;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import com.motadev.clone_reddit.user.repository.RoleRepository;
import com.motadev.clone_reddit.user.repository.UserRepository;
import com.motadev.clone_reddit.user.service.UserServiceI;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserServiceI {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository, PasswordEncoder
                                   passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void register(UserRequestDTO userRequest) {
        var basicRole = roleRepository.findByName(RoleValues.BASIC.name())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found."));

        var userFromDb = userRepository.findByUsername(userRequest.username());
        if (userFromDb.isPresent()) {
            throw new ResourceAlreadyExists("Resource Already Exists.");
        }

        var user = new User();
        user.setUsername(userRequest.username());
        user.setPassword(passwordEncoder.encode(userRequest.password()));
        user.setRoles(Set.of(basicRole));

        userRepository.save(user);
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
