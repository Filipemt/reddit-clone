package com.motadev.clone_reddit.auth.service;

import com.motadev.clone_reddit.auth.dtos.request.LoginRequest;
import com.motadev.clone_reddit.auth.dtos.response.TokenData;

public interface AuthenticationServiceI {
    TokenData authenticate(LoginRequest loginRequest);
}
