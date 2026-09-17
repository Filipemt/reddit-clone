package com.motadev.clone_reddit.auth.controller;

import com.motadev.clone_reddit.auth.dtos.request.LoginRequest;
import com.motadev.clone_reddit.auth.dtos.request.RefreshTokenRequest;
import com.motadev.clone_reddit.auth.dtos.request.RevokeRequest;
import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.auth.service.AuthenticationServiceI;
import com.motadev.clone_reddit.auth.service.RefreshTokenServiceI;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/authentication")
public class AuthenticationController {
    private final AuthenticationServiceI authenticationServiceI;
    private final RefreshTokenServiceI refreshTokenServiceI;

    public AuthenticationController(AuthenticationServiceI authenticationServiceI,
                                    RefreshTokenServiceI refreshTokenServiceI) {
        this.authenticationServiceI = authenticationServiceI;
        this.refreshTokenServiceI = refreshTokenServiceI;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenData> authenticate(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authenticationServiceI.authenticate(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenData> refresh(@RequestBody @Valid RefreshTokenRequest refreshToken) {
        return ResponseEntity.ok(refreshTokenServiceI.refresh(refreshToken.refreshToken()));
    }

    @DeleteMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid RevokeRequest request) {
        refreshTokenServiceI.revoke(request.tokenValue());
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
