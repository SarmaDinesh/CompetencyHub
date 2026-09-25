package com.example.CompetencyHub.web.dto.request;

import jakarta.validation.constraints.NotBlank;

/** No @Email or @Size here: a login form reveals nothing about the rules for valid accounts. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
