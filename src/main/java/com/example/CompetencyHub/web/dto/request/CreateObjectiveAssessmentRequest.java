package com.example.CompetencyHub.web.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An automatically scored test. See {@link CreateAssessmentRequest} for how Jackson picks
 * this record.
 *
 * <p>{@code @Valid} on the controller parameter still works even though the parameter is
 * declared as the interface: Bean Validation checks the constraints of the object's RUNTIME
 * class, so these annotations are the ones that run.
 */
public record CreateObjectiveAssessmentRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200)
        String title,

        @NotNull(message = "minScore is required")
        @Min(value = 0, message = "minScore cannot be negative")
        Integer minScore,

        @NotNull(message = "maxScore is required")
        Integer maxScore,

        @NotNull(message = "questionCount is required")
        @Min(value = 1, message = "A test needs at least one question")
        Integer questionCount,

        @NotNull(message = "passingScore is required")
        Integer passingScore

) implements CreateAssessmentRequest {

    /*
     * Cross-field rules. A single-field annotation like @Min cannot compare two fields, so
     * the rule is a boolean method and @AssertTrue checks it returns true. The "is" prefix
     * matters: Bean Validation only looks at getter-shaped methods, and it reports the
     * failure under the derived property name ("scoreRangeValid") in fieldErrors.
     *
     * Each returns true when an input is null, leaving that failure to @NotNull. Otherwise
     * one missing field would produce two errors, one of them misleading.
     *
     * Why here and not only in ScoreRange's constructor? ScoreRange throws
     * IllegalArgumentException, which the advice maps to a 500. Checking at the edge gives
     * the client a 400 naming the field; the entity check stays as the last line of defence.
     */
    @AssertTrue(message = "minScore must be less than maxScore")
    public boolean isScoreRangeValid() {
        return minScore == null || maxScore == null || minScore < maxScore;
    }

    @AssertTrue(message = "passingScore must be between minScore and maxScore")
    public boolean isPassingScoreInRange() {
        return passingScore == null || minScore == null || maxScore == null
                || (passingScore >= minScore && passingScore <= maxScore);
    }
}
