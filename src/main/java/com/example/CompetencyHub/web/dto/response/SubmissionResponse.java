package com.example.CompetencyHub.web.dto.response;

import com.example.CompetencyHub.domain.model.Mentor;
import com.example.CompetencyHub.domain.model.Submission;

import java.time.LocalDateTime;

/**
 * A submission as the API returns it.
 *
 * <p>Only IDs of the related student, assessment and mentor -- not nested objects. That is
 * a contract choice and a performance one: getId() on a lazy proxy reads the id the proxy
 * already holds, so mapping a list of 50 submissions costs zero extra queries. Asking for
 * the student's name here would silently cost one query per row (N+1), and outside the
 * transaction it would throw instead.
 */
public record SubmissionResponse(
        Long id,
        Long studentId,
        Long assessmentId,
        int attemptNumber,
        String status,
        Integer score,
        String content,
        String feedback,
        LocalDateTime submittedAt,
        LocalDateTime gradedAt,
        Long gradedByMentorId
) {
    public static SubmissionResponse from(Submission s) {
        Mentor grader = s.getGradedBy();
        return new SubmissionResponse(
                s.getId(),
                s.getStudent().getId(),
                s.getAssessment().getId(),
                s.getAttemptNumber(),
                s.getStatus().name(),
                s.getScore(),
                s.getContent(),
                s.getFeedback(),
                s.getSubmittedAt(),
                s.getGradedAt(),
                grader == null ? null : grader.getId()
        );
    }
}
