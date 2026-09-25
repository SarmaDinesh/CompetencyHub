package com.example.CompetencyHub.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/auth/register. Always creates a STUDENT: there is no role field, because
 * a public endpoint that let the caller choose their role would let anyone register as ADMIN.
 * Mentors are created by an admin; the first admin is seeded.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,

        /*
         * 72 is not arbitrary: bcrypt only reads the first 72 BYTES of a password and silently
         * ignores the rest. Without the cap, "correct horse ... [72 chars] ...A" and
         * "... ...B" would be the same password. Rejecting longer input makes that visible.
         */
        @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String password
) {
}
