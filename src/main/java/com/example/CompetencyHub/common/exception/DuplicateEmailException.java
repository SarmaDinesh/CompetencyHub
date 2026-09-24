package com.example.CompetencyHub.common.exception;

/** Someone is already registered with this email. 409, like DuplicateCourseCodeException. */
public class DuplicateEmailException extends BusinessRuleException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
