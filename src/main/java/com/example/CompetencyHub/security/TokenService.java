package com.example.CompetencyHub.security;

import com.example.CompetencyHub.domain.model.AppUser;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Issues access tokens.
 *
 * <p><b>What a JWT is.</b> Three base64 parts separated by dots: header.payload.signature.
 * The payload is NOT encrypted -- anyone can decode it and read the claims (paste one into
 * jwt.io). What they cannot do is CHANGE a claim, because the signature would no longer
 * match. So a token can carry "this is student 7, role STUDENT" and the server can trust it
 * without a database lookup -- but it must never carry a secret.
 *
 * <p><b>Claims we put in:</b>
 * <ul>
 *   <li>{@code sub} -- the email, the account's stable identifier</li>
 *   <li>{@code roles} -- e.g. ["STUDENT"]; SecurityConfig turns these into ROLE_ authorities</li>
 *   <li>{@code uid}, and {@code studentId} or {@code mentorId} when the account has one --
 *       so ownership checks ("is this YOUR submission?") compare numbers from the token
 *       instead of querying the database on every request</li>
 *   <li>{@code iss}, {@code iat}, {@code exp} -- issuer, issued-at, expiry</li>
 * </ul>
 *
 * <p><b>The trade-off of stateless tokens.</b> Nothing is stored server-side, so there is no
 * session table and any instance can verify any token. The price: a token cannot be revoked
 * before it expires. Disable an account and its outstanding token still works until exp.
 * That is why the TTL is short (1 hour). Longer sessions use a refresh token, which IS stored
 * and CAN be revoked -- a deliberate follow-up, not in this branch.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public IssuedToken issue(AppUser user, Long studentId, Long mentorId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.ttl());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .claim("uid", user.getId());
        if (studentId != null) claims.claim("studentId", studentId);
        if (mentorId != null) claims.claim("mentorId", mentorId);

        // RS256 named explicitly: the encoder picks the key from the JWK set by algorithm.
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();

        return new IssuedToken(value, expiresAt, properties.ttl().toSeconds());
    }

    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) { }
}
