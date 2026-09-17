package com.motadev.clone_reddit.auth.service.impl;

import com.motadev.clone_reddit.auth.dtos.request.LoginRequest;
import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.auth.service.AuthenticationServiceI;
import com.motadev.clone_reddit.auth.service.RefreshTokenServiceI;
import com.motadev.clone_reddit.auth.service.TokenServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.user.service.UserServiceI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationServiceI {

    private final UserServiceI userService;
    private final TokenServiceI tokenService;
    private final RefreshTokenServiceI refreshTokenService;

    public AuthenticationServiceImpl(UserServiceI userService,
                                     TokenServiceI tokenService,
                                     RefreshTokenServiceI refreshTokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public TokenData authenticate(LoginRequest loginRequest) {
        var authInfo = userService.validateCredentials(loginRequest.username(), loginRequest.password())
                .orElseThrow(() -> new ResourceInvalidException("User or Password Invalid."));

        var refreshToken = refreshTokenService.createRefreshToken(authInfo);
        return tokenService.generateToken(authInfo, refreshToken.getToken());
    }
}
