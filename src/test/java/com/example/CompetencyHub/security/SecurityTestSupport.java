package com.example.CompetencyHub.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * "Act as" helpers for MockMvc: {@code mockMvc.perform(get("/api/x").with(asStudent(7L)))}.
 *
 * <p>Each builds the same claims TokenService puts in a real token, and sets the authorities
 * explicitly. Without {@code .authorities(...)}, jwt() derives them from a "scope" claim --
 * the resource server's default, which this project replaced with "roles" -- and every
 * hasRole check would fail.
 */
public final class SecurityTestSupport {

    private SecurityTestSupport() { }

    public static JwtRequestPostProcessor asAdmin() {
        return as("admin@example.com", "ADMIN", null, null);
    }

    public static JwtRequestPostProcessor asStudent(Long studentId) {
        return as("student" + studentId + "@example.com", "STUDENT", studentId, null);
    }

    public static JwtRequestPostProcessor asMentor(Long mentorId) {
        return as("mentor" + mentorId + "@example.com", "MENTOR", null, mentorId);
    }

    private static JwtRequestPostProcessor as(String email, String role, Long studentId, Long mentorId) {
        return jwt()
                .jwt(token -> {
                    token.subject(email).claim("roles", List.of(role));
                    if (studentId != null) token.claim("studentId", studentId);
                    if (mentorId != null) token.claim("mentorId", mentorId);
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
