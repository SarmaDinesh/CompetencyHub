package com.example.CompetencyHub.web.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * A task graded by a mentor against a rubric. rubricUrl and wordLimit are optional, matching
 * the nullable columns in V3.
 */
public record CreatePerformanceAssessmentRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200)
        String title,

        @NotNull(message = "minScore is required")
        @Min(value = 0, message = "minScore cannot be negative")
        Integer minScore,

        @NotNull(message = "maxScore is required")
        Integer maxScore,

        // Hibernate Validator's own constraint, not part of the jakarta standard set.
        // Null passes; only a present-but-malformed value fails.
        @URL(message = "rubricUrl must be a valid URL")
        @Size(max = 500)
        String rubricUrl,

        @Min(value = 1, message = "wordLimit must be at least 1 when given")
        Integer wordLimit

) implements CreateAssessmentRequest {

    @AssertTrue(message = "minScore must be less than maxScore")
    public boolean isScoreRangeValid() {
        return minScore == null || maxScore == null || minScore < maxScore;
    }
}
