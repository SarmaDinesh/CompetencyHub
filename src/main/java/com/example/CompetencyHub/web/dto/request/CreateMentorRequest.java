package com.example.CompetencyHub.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMentorRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email(message = "Must be a valid email address") @Size(max = 255) String email,
        @Size(max = 200) String specialization,

        /*
         * The mentor's initial password, set by the admin creating them. A real system would
         * email an invite link instead so the admin never knows it -- noted as a follow-up.
         * Same 8-72 rule as registration (bcrypt reads only 72 bytes).
         */
        @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String password
) {
}
