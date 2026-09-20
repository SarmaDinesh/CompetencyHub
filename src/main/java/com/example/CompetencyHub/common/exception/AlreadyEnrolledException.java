package com.example.CompetencyHub.common.exception;

/** The student already holds an enrollment in this course. */
public class AlreadyEnrolledException extends BusinessRuleException {
    public AlreadyEnrolledException(String message) {
        super(message);
    }
}