package com.example.CompetencyHub.web.dto.request;

import jakarta.validation.constraints.*;

/**
 * Body of POST /api/courses.
 *
 * <p>A dedicated request type, not the Course entity. Three reasons:
 * <ul>
 *   <li>It states exactly what a client may send. Binding to the entity would let
 *       someone POST an {@code id} or a {@code version} and quietly corrupt state.</li>
 *   <li>Validation rules for input differ from domain invariants. Here they describe
 *       a well-formed request; the entity still enforces the rules that must hold
 *       however an object is created.</li>
 *   <li>The API contract can stay stable while the entity changes.</li>
 * </ul>
 *
 * <p>Constraints are checked before the controller method body runs, so by the time
 * the service sees this object it is structurally valid.
 */
public record CreateCourseRequest(

        @NotBlank(message = "Course code is required")
        @Pattern(regexp = "^[A-Z]{2,4}\\d{3}$",
                message = "Course code must be 2-4 letters followed by 3 digits, e.g. CS544")
        String code,

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        // Not @NotNull: omitting capacity is allowed and falls back to the configured
        // default. Validation only applies when a value is supplied.
        @Min(value = 1, message = "Capacity must be at least 1")
        @Max(value = 500, message = "Capacity must be at most 500")
        Integer capacity
) {
}
