package com.ridelink.driver_service.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/** Builds signed JWTs the way the Account Service would, for tests only. */
public final class TestTokens {

    private TestTokens() {
    }

    public static String token(String secret, String subject, String role, Instant expiry) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject(subject).claim("role", role);
            if (expiry != null) {
                claims.expirationTime(Date.from(expiry));
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String validToken(String secret, String subject, String role) {
        return token(secret, subject, role, Instant.now().plusSeconds(3600));
    }
}