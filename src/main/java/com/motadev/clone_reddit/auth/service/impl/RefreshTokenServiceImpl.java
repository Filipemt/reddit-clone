package com.motadev.clone_reddit.auth.service.impl;

import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.auth.entity.RefreshToken;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;
import com.motadev.clone_reddit.auth.repository.RefreshTokenRepository;
import com.motadev.clone_reddit.auth.service.RefreshTokenServiceI;
import com.motadev.clone_reddit.auth.service.TokenServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.user.service.UserServiceI;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenServiceI {

    private final UserServiceI userService;
    private final RefreshTokenRepository refreshRepository;
    private final TokenServiceI tokenService;

    @Value("${jwt.refreshExpirationMs}")
    private long refreshExpirationMs;

    public RefreshTokenServiceImpl(UserServiceI userService,
                                   RefreshTokenRepository refreshRepository,
                                   TokenServiceI tokenService) {
        this.userService = userService;
        this.refreshRepository = refreshRepository;
        this.tokenService = tokenService;
    }

    @Override
    @Transactional
    public RefreshToken createRefreshToken(UserAuthInfo authInfo) {
        var token = new RefreshToken();
        token.setUserId(authInfo.userId());
        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(Instant.now().plusMillis(refreshExpirationMs));

        return refreshRepository.save(token);
    }

    @Override
    @Transactional
    public TokenData refresh(String tokenValue) {
        RefreshToken oldRefresh = verify(tokenValue);
        revoke(oldRefresh.getToken());

        UserAuthInfo authInfo = userService.findAuthInfoById(oldRefresh.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User no longer exists."));

        RefreshToken newRefresh = createRefreshToken(authInfo);
        return tokenService.generateToken(authInfo, newRefresh.getToken());
    }

    @Override
    @Transactional
    public void revoke(String tokenValue) {
        refreshRepository.findByToken(tokenValue)
                .ifPresent(refreshToken -> {
                    refreshToken.setRevoked(true);
                    refreshRepository.save(refreshToken);
                });
    }

    public RefreshToken verify(String tokenValue) {
        RefreshToken token = refreshRepository.findByToken(tokenValue)
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token not found"));

        if (token.isRevoked() || token.getExpiryDate().isBefore(Instant.now())) {
            throw new ResourceInvalidException("Refresh token invalid or expired");
        }

        return token;
    }
}
