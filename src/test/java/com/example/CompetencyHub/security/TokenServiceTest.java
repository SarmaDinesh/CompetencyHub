package com.example.CompetencyHub.security;

import com.example.CompetencyHub.domain.enums.Role;
import com.example.CompetencyHub.domain.model.AppUser;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issue a token, then verify it with the same decoder the resource server uses. No Spring
 * context: JwtConfig's @Bean methods are plain methods and can be called directly.
 */
class TokenServiceTest {

    private final JwtConfig config = new JwtConfig();
    private final JwtProperties properties = new JwtProperties("competencyhub", Duration.ofHours(1), null, null);

    private TokenService tokenService;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        RSAKey key = config.jwtSigningKey(properties);
        tokenService = new TokenService(config.jwtEncoder(key), properties);
        decoder = config.jwtDecoder(key, properties);
    }

    @Test
    void aStudentTokenCarriesIdentityRoleAndStudentId() {
        String token = tokenService.issue(user("ada@example.com", Role.STUDENT, 5L), 7L, null).value();

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("ada@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("STUDENT");
        assertThat(((Number) jwt.getClaims().get("studentId")).longValue()).isEqualTo(7L);
        assertThat(jwt.getClaims()).doesNotContainKey("mentorId");
        assertThat(jwt.getIssuer().toString()).isEqualTo("competencyhub");
    }

    @Test
    void aTokenSignedByAnotherKeyIsRejected() throws Exception {
        // Same code, same issuer, different key pair: exactly what a forger would have.
        RSAKey otherKey = config.jwtSigningKey(properties);
        TokenService forger = new TokenService(config.jwtEncoder(otherKey), properties);
        String forged = forger.issue(user("mallory@example.com", Role.ADMIN, 666L), null, null).value();

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenFromAnotherIssuerIsRejected() throws Exception {
        RSAKey key = config.jwtSigningKey(properties);
        JwtProperties otherIssuer = new JwtProperties("someone-else", Duration.ofHours(1), null, null);
        String token = new TokenService(config.jwtEncoder(key), otherIssuer)
                .issue(user("ada@example.com", Role.STUDENT, 5L), 7L, null).value();

        // Correct signature, wrong "iss": still refused.
        assertThatThrownBy(() -> config.jwtDecoder(key, properties).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void anExpiredTokenIsRejected() throws Exception {
        // Negative TTL: expired before it was issued -- and beyond the 60s clock-skew allowance.
        JwtProperties expired = new JwtProperties("competencyhub", Duration.ofMinutes(-5), null, null);
        RSAKey key = config.jwtSigningKey(properties);
        String token = new TokenService(config.jwtEncoder(key), expired)
                .issue(user("ada@example.com", Role.STUDENT, 5L), 7L, null).value();

        assertThatThrownBy(() -> config.jwtDecoder(key, properties).decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void configuringOnlyOneKeyFailsAtStartup() {
        JwtProperties half = new JwtProperties("competencyhub", Duration.ofHours(1),
                new ByteArrayResource(new byte[0]), null);

        assertThatThrownBy(() -> config.jwtSigningKey(half))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both");
    }

    private static AppUser user(String email, Role role, Long id) {
        AppUser user = new AppUser(email, "{noop}unused", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
