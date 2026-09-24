package com.example.CompetencyHub.domain.enums;

/**
 * The submission lifecycle. Two states, one legal transition: SUBMITTED -> GRADED.
 * Objective tests pass through SUBMITTED within the same transaction (auto-graded);
 * performance tasks wait there until a mentor grades them.
 */
public enum SubmissionStatus {
    SUBMITTED, GRADED
}
