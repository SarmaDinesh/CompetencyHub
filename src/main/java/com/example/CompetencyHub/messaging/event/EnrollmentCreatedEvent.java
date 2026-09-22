package com.example.CompetencyHub.messaging.event;

import java.time.Instant;

/**
 * Published when a student successfully enrols in a course.
 *
 * <p><b>This is a published contract, not an internal class.</b> Once a consumer
 * elsewhere reads this message, its shape belongs to everyone: renaming a field breaks
 * them silently. Evolve it by adding optional fields, never by removing or renaming.
 *
 * <p>Carries denormalised values (courseCode, courseTitle, studentEmail) rather than
 * ids alone. A consumer that received only ids would have to call back into this service
 * to do anything useful — which reintroduces the coupling the message was meant to remove.
 * Messages should be self-contained.
 */
public record EnrollmentCreatedEvent(
        Long enrollmentId,
        Long studentId,
        String studentEmail,
        Long courseId,
        String courseCode,
        String courseTitle,
        Instant occurredAt
) {
}
