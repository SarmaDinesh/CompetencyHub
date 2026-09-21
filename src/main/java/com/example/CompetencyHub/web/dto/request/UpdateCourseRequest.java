package com.example.CompetencyHub.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/courses/{id}.
 *
 * <p>No {@code code} field: the course code identifies the course to the outside world,
 * and letting it change would break every reference anyone holds. Fields that must not
 * change simply do not appear in the update contract — clearer than accepting the value
 * and silently ignoring it.
 *
 * <p>PUT means "replace the resource with this", so every updatable field is required.
 * Partial updates would be PATCH with a different request type where fields are optional.
 */
public record UpdateCourseRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200)
        String title,

        @Size(max = 2000)
        String description,

        @NotNull(message = "Capacity is required")
        @Min(1) @Max(500)
        Integer capacity
) {
}