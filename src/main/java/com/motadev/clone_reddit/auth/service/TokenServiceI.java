package com.motadev.clone_reddit.auth.service;

import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;

public interface TokenServiceI {
    TokenData generateToken(UserAuthInfo userAuthInfo, String refreshToken);
}
