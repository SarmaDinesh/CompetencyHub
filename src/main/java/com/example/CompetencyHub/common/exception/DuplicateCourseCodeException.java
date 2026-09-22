package com.example.CompetencyHub.common.exception;

/**
 * A course with this code already exists.
 *
 * <p>Extends BusinessRuleException so the existing handler maps it to 409 Conflict.
 * Without this check the database's unique constraint would still prevent the duplicate,
 * but it would surface as a DataIntegrityViolationException and a 500 — a rule the user
 * broke reported as a server failure.
 */
public class DuplicateCourseCodeException extends BusinessRuleException {
    public DuplicateCourseCodeException(String message) {
        super(message);
    }
}