package com.example.CompetencyHub.common.exception;

/** No seats remain on the requested course. */
public class CourseFullException extends BusinessRuleException {
    public CourseFullException(String message) {
        super(message);
    }
}