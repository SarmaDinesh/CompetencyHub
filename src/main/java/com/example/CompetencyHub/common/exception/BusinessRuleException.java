package com.example.CompetencyHub.common.exception;

/**
 * Base for violations of a domain rule — the request was well formed, but the system's
 * rules forbid it. Maps to HTTP 409 Conflict.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
