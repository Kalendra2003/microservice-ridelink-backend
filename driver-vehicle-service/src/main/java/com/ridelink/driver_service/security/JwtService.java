package com.ridelink.driver_service.security;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ridelink.driver_service.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies HS256 JWTs issued by the Account Service.
 * Expected claims: sub (user id), role (PASSENGER|DRIVER|ADMIN|SERVICE), exp.
 */
@Component
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final MACVerifier verifier;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("app.jwt.secret (JWT_SECRET) must be at least 32 characters");
        }
        try {
            this.verifier = new MACVerifier(secret.getBytes(StandardCharsets.UTF_8));
        } catch (JOSEException e) {
            throw new IllegalArgumentException("Invalid JWT secret", e);
        }
    }

    public AuthenticatedUser parse(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                throw new UnauthorizedException("Invalid token signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            Date expiry = claims.getExpirationTime();
            if (expiry == null || expiry.before(new Date())) {
                throw new UnauthorizedException("Token has expired or has no expiry");
            }
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new UnauthorizedException("Token has no subject");
            }
            return new AuthenticatedUser(subject, toRole(claims.getStringClaim("role")));
        } catch (ParseException | JOSEException e) {
            throw new UnauthorizedException("Invalid token");
        }
    }

    private static Role toRole(String claim) {
        if (claim == null || claim.isBlank()) {
            throw new UnauthorizedException("Token has no role");
        }
        String normalised = claim.trim().toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", "");
        try {
            return Role.valueOf(normalised);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Token contains an unknown role");
        }
    }
}