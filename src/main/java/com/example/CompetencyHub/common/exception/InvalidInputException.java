package com.example.CompetencyHub.common.exception;

/**
 * The request is well formed but its values are wrong for THIS target, and only the
 * service could tell -- a score of 150 on a 0-100 assessment, a performance task sent with a
 * score. Bean Validation on the DTO cannot see the assessment, so it cannot catch these.
 *
 * <p>Maps to 400, not 409. The difference matters to a client: 400 means "fix your request
 * and it can work", 409 means "your request is fine, the current state forbids it".
 */
public class InvalidInputException extends RuntimeException {
    public InvalidInputException(String message) {
        super(message);
    }
}
