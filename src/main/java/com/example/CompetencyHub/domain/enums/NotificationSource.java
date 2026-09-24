package com.example.CompetencyHub.domain.enums;

/**
 * Which kind of event produced a notification. Together with the source id it identifies
 * the event uniquely -- enrollment 5 and submission 5 are different events.
 */
public enum NotificationSource {
    ENROLLMENT_CREATED, SUBMISSION_GRADED
}
