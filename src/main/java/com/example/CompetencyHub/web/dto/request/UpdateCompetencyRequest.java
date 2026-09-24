package com.example.CompetencyHub.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/competencies/{id}. PUT replaces, so every field is required -- same
 * contract as UpdateCourseRequest.
 */
public record UpdateCompetencyRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @NotNull(message = "Weight is required")
        @Min(1) @Max(100)
        Integer weight,

        @NotNull(message = "Order index is required")
        @Min(1)
        Integer orderIndex
) {
}
