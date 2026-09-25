package com.example.CompetencyHub.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * Reads the caller's identity from the verified token for the current request.
 *
 * <p>SecurityContextHolder is thread-bound: the resource-server filter put the
 * authentication there at the start of this request, and it is cleared at the end. Every
 * call here is about the person making THIS request.
 */
public final class CurrentUser {

    private CurrentUser() { }

    public static Optional<Jwt> jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken token ? Optional.of(token.getToken()) : Optional.empty();
    }

    public static Optional<Long> studentId() {
        return longClaim("studentId");
    }

    public static Optional<Long> mentorId() {
        return longClaim("mentorId");
    }

    public static Optional<String> email() {
        return jwt().map(Jwt::getSubject);
    }

    /*
     * JSON has no integer type distinct from other numbers. Nimbus decodes whole numbers as
     * Long, but a hand-built test token could hold an Integer -- Number covers both. Comparing
     * a Long claim to an Integer with equals() is false even for the same value, a classic.
     */
    private static Optional<Long> longClaim(String name) {
        return jwt().map(j -> j.getClaims().get(name))
                .filter(Number.class::isInstance)
                .map(v -> ((Number) v).longValue());
    }
}
