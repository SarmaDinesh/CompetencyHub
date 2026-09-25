package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.enums.Role;

import java.time.Instant;

public interface AuthService {

    /** Creates a STUDENT login and its student profile, and logs it in. */
    AuthResult register(String firstName, String lastName, String email, String rawPassword);

    AuthResult login(String email, String rawPassword);

    /**
     * Service-level result. The controller maps it to the HTTP response -- the service does
     * not know the JSON field names, same rule as AssessmentService taking plain values.
     */
    record AuthResult(String token, Instant expiresAt, long expiresInSeconds,
                      String email, Role role, Long studentId, Long mentorId) { }
}
