package com.motadev.clone_reddit.auth.service;

import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.auth.entity.User;

public interface TokenServiceI {
    TokenData generateToken(User user);
}
