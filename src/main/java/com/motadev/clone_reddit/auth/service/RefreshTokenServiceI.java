package com.motadev.clone_reddit.auth.service;

import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.auth.entity.RefreshToken;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;

public interface RefreshTokenServiceI {
    RefreshToken createRefreshToken(UserAuthInfo authInfo);
    TokenData refresh(String tokenValue);
    void revoke(String tokenValue);
}
