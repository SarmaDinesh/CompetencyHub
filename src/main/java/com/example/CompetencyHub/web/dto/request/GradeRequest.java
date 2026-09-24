package com.example.CompetencyHub.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/submissions/{id}/grade. mentorId moves to the security context once
 * authentication exists -- same note as SubmitRequest.studentId.
 */
public record GradeRequest(

        @NotNull(message = "mentorId is required")
        Long mentorId,

        @NotNull(message = "score is required")
        Integer score,

        @Size(max = 5_000, message = "feedback must be at most 5,000 characters")
        String feedback
) {
}
