package com.example.CompetencyHub.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Token settings, bound from competencyhub.security.jwt.*. Same constructor-bound record
 * style as JobProperties.
 *
 * @param issuer     written into every token's "iss" claim and checked on every request. A
 *                   token from some other system, even one signed correctly, is rejected.
 * @param ttl        how long a token lives. Short on purpose: a JWT cannot be revoked once
 *                   issued (see TokenService), so its lifetime IS the damage window if one leaks.
 * @param publicKey  PEM file (e.g. file:/run/secrets/jwt-public.pem). Optional -- see JwtConfig.
 * @param privateKey PEM file, PKCS#8. Optional, but must be given together with publicKey.
 */
@ConfigurationProperties(prefix = "competencyhub.security.jwt")
@Validated
public record JwtProperties(
        @NotBlank String issuer,
        @NotNull Duration ttl,
        Resource publicKey,
        Resource privateKey
) {
}
