package com.motadev.clone_reddit.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.converter.RsaKeyConverters;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

public final class TestJwtBuilder {

    private TestJwtBuilder() {
    }

    public static RSAPrivateKey loadPrivateKey() {
        return loadKey("test-app.key", RsaKeyConverters.pkcs8());
    }

    public static RSAPublicKey loadPublicKey() {
        return loadKey("test-app.pub", RsaKeyConverters.x509());
    }

    // Gera um segundo par RSA para forjar tokens com assinatura errada.
    public static KeyPair additionalKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    // Monta um JWT assinado com a chave informada e claims arbitrarios.
    public static String buildSignedToken(RSAPrivateKey signingKey,
                                          String issuer,
                                          String subject,
                                          Instant issuedAt,
                                          Instant expiresAt,
                                          String scope) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .claim("scope", scope)
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to sign JWT", e);
        }
        return jwt.serialize();
    }

    private static <T> T loadKey(String resource, org.springframework.core.convert.converter.Converter<InputStream, T> converter) {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            return converter.convert(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load test key: " + resource, e);
        }
    }
}