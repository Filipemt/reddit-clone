package com.motadev.clone_reddit.auth.service.impl;

import com.motadev.clone_reddit.auth.dtos.request.LoginRequest;
import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.auth.logging.AuthEventLog;
import com.motadev.clone_reddit.auth.service.AuthenticationServiceI;
import com.motadev.clone_reddit.auth.service.RefreshTokenServiceI;
import com.motadev.clone_reddit.auth.service.TokenServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.user.service.UserServiceI;
import org.springframework.stereotype.Service;


@Service
public class AuthenticationServiceImpl implements AuthenticationServiceI {

    private final UserServiceI userService;
    private final TokenServiceI tokenService;
    private final RefreshTokenServiceI refreshTokenService;
    private final AuthEventLog authEventLog;

    public AuthenticationServiceImpl(UserServiceI userService,
                                     TokenServiceI tokenService,
                                     RefreshTokenServiceI refreshTokenService,
                                     AuthEventLog authEventLog) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.authEventLog = authEventLog;
    }

    @Override
    public TokenData authenticate(LoginRequest loginRequest) {
        var authInfo = userService.validateCredentials(loginRequest.username(), loginRequest.password())
                .orElseThrow(() -> {
                    authEventLog.loginFailed(loginRequest.username());
                    return new ResourceInvalidException("User or Password Invalid.");
                });

        authEventLog.loginSuccess(authInfo.userId(), loginRequest.username());

        var refreshToken = refreshTokenService.createRefreshToken(authInfo);
        return tokenService.generateToken(authInfo, refreshToken.getToken());
    }
}
