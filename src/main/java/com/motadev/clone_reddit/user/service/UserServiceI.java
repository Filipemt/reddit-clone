package com.motadev.clone_reddit.user.service;

import com.motadev.clone_reddit.user.dtos.request.UserRequestDTO;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;
import com.motadev.clone_reddit.user.dtos.response.UserResponseDTO;

import java.util.Optional;
import java.util.UUID;

public interface UserServiceI {
    void register(UserRequestDTO userRequestDTO);
    UserResponseDTO getUserById(UUID userId);
    Optional<UserAuthInfo> validateCredentials(String username, String rawPassword);
    Optional<UserAuthInfo> findAuthInfoById(UUID userId);
    void softDeleteMyAccount();
}
