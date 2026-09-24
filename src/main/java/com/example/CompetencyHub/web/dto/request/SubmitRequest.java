package com.example.CompetencyHub.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/assessments/{id}/submissions.
 *
 * <p>score and content are both optional HERE, and required THERE: which one is needed
 * depends on the assessment's type, which this record cannot see. The service enforces it
 * and answers 400 (InvalidInputException) when the wrong one is sent.
 *
 * <p>Why not a polymorphic body with a "type" field, like assessment creation? Because the
 * client does not choose the type here -- the assessment already has one. Asking the client
 * to repeat it would only create a way to send a mismatch.
 *
 * <p>studentId travels in the body for now. Once security lands, it comes from the logged-in
 * user instead; a student must not be able to submit on someone else's behalf.
 */
public record SubmitRequest(

        @NotNull(message = "studentId is required")
        Long studentId,

        /* Objective tests only. Range is checked against the assessment in the service. */
        Integer score,

        /* Performance tasks only. */
        @Size(max = 50_000, message = "content must be at most 50,000 characters")
        String content
) {
}
