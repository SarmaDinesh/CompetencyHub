package com.example.CompetencyHub.domain.enums;

public enum AttemptOutcome {
    /** Seat reserved and enrollment created. */
    SUCCESS,
    /** A domain rule refused the request — course full, or already enrolled. */
    REJECTED,
    /** Another transaction won the race for the same row. */
    CONFLICT
}
