package com.motadev.clone_reddit.user.service.impl;

import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
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
        validateUniqueUser(userRequest);
        Role basicRole = roleRepository.findByName(RoleValues.BASIC.name())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found."));

        String encodedPassword = passwordEncoder.encode(userRequest.password());
        userRepository.save(userConvert.convertDtoToEntity(userRequest, encodedPassword, basicRole));
    }

    @Override
    public UserResponseDTO getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        return userConvert.convertEntityToDTo(user);
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

    private void validateUniqueUser(UserRequestDTO userRequest) {
        if (userRepository.existsByEmail(userRequest.username())) {
            throw new ResourceAlreadyExists("Resource Already Exists.");
        }
        if (userRepository.existsByEmail(userRequest.email())) {
            throw new ResourceAlreadyExists("Resource Already Exists.");
        }
    }
}
