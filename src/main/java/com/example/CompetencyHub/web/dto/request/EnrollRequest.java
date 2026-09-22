package com.example.CompetencyHub.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record EnrollRequest(
        @NotNull(message = "studentId is required")
        Long studentId
) {
}
