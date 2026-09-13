package com.motadev.clone_reddit.auth.service.impl;

import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.auth.entity.RefreshToken;
import com.motadev.clone_reddit.auth.entity.User;
import com.motadev.clone_reddit.auth.repository.RefreshTokenRepository;
import com.motadev.clone_reddit.auth.service.RefreshTokenServiceI;
import com.motadev.clone_reddit.auth.service.TokenServiceI;
import com.motadev.clone_reddit.shared.exception.ResourceInvalidException;
import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenServiceI {
    private final RefreshTokenRepository refreshRepository;
    private final TokenServiceI tokenService;

    @Value("${jwt.refreshExpirationMs}")
    private long refreshExpirationMs;

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshRepository,
                                   TokenServiceI tokenService) {
        this.refreshRepository = refreshRepository;
        this.tokenService = tokenService;
    }

    @Override
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        var token = new RefreshToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(Instant.now().plusMillis(refreshExpirationMs));

        return refreshRepository.save(token);
    }

    @Override
    @Transactional
    public TokenData refresh(String tokenValue) {
        RefreshToken oldRefresh = verify(tokenValue);
        revoke(oldRefresh.getToken());

        RefreshToken newRefresh = createRefreshToken(oldRefresh.getUser());

        TokenData newAccessToken = tokenService.generateToken(oldRefresh.getUser(), newRefresh.getToken());

        return new TokenData(newAccessToken.accessToken(), newAccessToken.expiresIn(), newRefresh.getToken());
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
