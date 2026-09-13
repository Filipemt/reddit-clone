package com.motadev.clone_reddit.auth.service.impl;

import com.motadev.clone_reddit.auth.dtos.request.LoginRequest;
import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.auth.entity.RefreshToken;
import com.motadev.clone_reddit.auth.entity.User;
import com.motadev.clone_reddit.auth.entity.enums.RoleValues;
import com.motadev.clone_reddit.auth.repository.RoleRepository;
import com.motadev.clone_reddit.auth.repository.UserRepository;
import com.motadev.clone_reddit.auth.service.AuthenticationServiceI;
import com.motadev.clone_reddit.auth.service.RefreshTokenServiceI;
import com.motadev.clone_reddit.auth.service.TokenServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceAlreadyExists;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationServiceI {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenServiceI tokenService;
    private final RefreshTokenServiceI refreshTokenService;

    public AuthenticationServiceImpl(UserRepository userRepository,
                                     PasswordEncoder passwordEncoder,
                                     TokenServiceI tokenService,
                                     RoleRepository roleRepository,
                                     RefreshTokenServiceI refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.roleRepository = roleRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public TokenData authenticate(LoginRequest loginRequest) {
        log.info("Buscando o usuário: {}", loginRequest.username());
        var user = userRepository.findByUsername(loginRequest.username())
                .orElseThrow(() -> new BadCredentialsException("User or Password is invalid."));

        if (!user.isLoginCorrect(loginRequest, passwordEncoder)) {
            throw new BadCredentialsException("User or Password is invalid.");
        }

        var refreshToken = refreshTokenService.createRefreshToken(user);
        return tokenService.generateToken(user, refreshToken.getToken());
    }

    @Override
    @Transactional
    public void register(LoginRequest loginRequest) {
        var basicRole = roleRepository.findByName(RoleValues.BASIC.name())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found."));

        var userFromDb = userRepository.findByUsername(loginRequest.username());
        if (userFromDb.isPresent()) {
            throw new ResourceAlreadyExists("Resource Already Exists.");
        }

        var user = new User();
        user.setUsername(loginRequest.username());
        user.setPassword(passwordEncoder.encode(loginRequest.password()));
        user.setRoles(Set.of(basicRole));

        userRepository.save(user);
    }
}
