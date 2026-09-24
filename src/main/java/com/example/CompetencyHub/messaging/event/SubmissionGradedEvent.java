package com.example.CompetencyHub.messaging.event;

import java.time.Instant;

/**
 * Published when a submission receives its grade -- automatically for an objective test,
 * or by a mentor for a performance task.
 *
 * <p>Same contract rules as EnrollmentCreatedEvent: self-contained (the consumer needs no
 * call back into this service to write "You scored 85 on Quiz 1 in CS544"), and evolved
 * only by adding optional fields.
 *
 * <p>Must stay in this package: the consumer's JSON deserializer only trusts classes from
 * {@code messaging.event} (spring.json.trusted.packages), so an event class anywhere else
 * would be refused at the consumer.
 */
public record SubmissionGradedEvent(
        Long submissionId,
        Long studentId,
        String studentEmail,
        Long assessmentId,
        String assessmentTitle,
        Long courseId,
        String courseCode,
        int score,
        boolean passed,
        /* true for an objective test scored on submission; false when a mentor graded it. */
        boolean autoGraded,
        Instant gradedAt
) {
}
