package com.example.CompetencyHub.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/courses/{courseId}/competencies.
 *
 * <p>No courseId field: the course comes from the URL. Accepting it in the body too would
 * mean two sources for one fact, and a request where they disagree.
 */
public record CreateCompetencyRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        /* How much this competency counts toward the course, 1-100. */
        @NotNull(message = "Weight is required")
        @Min(value = 1, message = "Weight must be at least 1")
        @Max(value = 100, message = "Weight must be at most 100")
        Integer weight,

        /*
         * Position within the course. Optional: omitted means "put it at the end", which is
         * what an admin adding a competency almost always wants.
         */
        @Min(value = 1, message = "Order index must be at least 1")
        Integer orderIndex
) {
}
