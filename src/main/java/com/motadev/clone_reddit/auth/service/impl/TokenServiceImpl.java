package com.motadev.clone_reddit.auth.service.impl;

import com.motadev.clone_reddit.auth.dtos.response.TokenData;
import com.motadev.clone_reddit.user.dtos.response.UserAuthInfo;
import com.motadev.clone_reddit.auth.service.TokenServiceI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.stream.Collectors;

@Service
public class TokenServiceImpl implements TokenServiceI {
    private final JwtEncoder jwtEncoder;

    @Value("${jwt.expiresIn}")
    private Long expiresIn;
    @Value("${jwt.issuer}")
    private String issuer;


    public TokenServiceImpl(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    @Override
    public TokenData generateToken(UserAuthInfo authInfo, String refreshToken) {
        var now = Instant.now();

        var scope = authInfo.roles()
                .stream()
                .sorted()
                .collect(Collectors.joining(" "));

        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(authInfo.userId().toString())
                .issuedAt(now)
                .claim("scope", scope)
                .expiresAt(now.plusSeconds(this.expiresIn))
                .build();

        var jwtValue = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        return new TokenData(jwtValue, expiresIn, refreshToken);
    }
}
