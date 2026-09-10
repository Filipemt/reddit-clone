package com.motadev.clone_reddit.auth.controller;

import com.motadev.clone_reddit.auth.dtos.request.LoginRequest;
import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.auth.service.AuthenticationServiceI;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/authentication")
public class AuthenticationController {
    private final AuthenticationServiceI authenticationServiceI;

    public AuthenticationController(AuthenticationServiceI authenticationServiceI) {
        this.authenticationServiceI = authenticationServiceI;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenData> authenticate(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authenticationServiceI.authenticate(request));
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody @Valid LoginRequest loginRequest) {
        authenticationServiceI.register(loginRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
