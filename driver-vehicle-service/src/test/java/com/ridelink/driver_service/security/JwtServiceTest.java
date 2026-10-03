package com.ridelink.driver_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.ridelink.driver_service.exception.UnauthorizedException;
import com.ridelink.driver_service.support.TestTokens;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";
    private final JwtService jwtService = new JwtService(SECRET);

    @Test
    void validTokenYieldsUserIdAndRole() {
        String token = TestTokens.validToken(SECRET, "acc-1", "DRIVER");

        AuthenticatedUser user = jwtService.parse(token);

        assertThat(user.userId()).isEqualTo("acc-1");
        assertThat(user.role()).isEqualTo(Role.DRIVER);
    }

    @Test
    void roleClaimIsCaseInsensitiveAndAcceptsRolePrefix() {
        String token = TestTokens.validToken(SECRET, "acc-2", "role_passenger");

        assertThat(jwtService.parse(token).role()).isEqualTo(Role.PASSENGER);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = TestTokens.validToken("another-secret-another-secret-123456", "acc-1", "DRIVER");

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void expiredTokenIsRejected() {
        String token = TestTokens.token(SECRET, "acc-1", "DRIVER", Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void tokenWithoutExpiryIsRejected() {
        String token = TestTokens.token(SECRET, "acc-1", "DRIVER", null);

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void unknownRoleIsRejected() {
        String token = TestTokens.validToken(SECRET, "acc-1", "SUPERUSER");

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("role");
    }

    @Test
    void garbageTokenIsRejected() {
        assertThatThrownBy(() -> jwtService.parse("not-a-jwt")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService("too-short")).isInstanceOf(IllegalArgumentException.class);
    }
}