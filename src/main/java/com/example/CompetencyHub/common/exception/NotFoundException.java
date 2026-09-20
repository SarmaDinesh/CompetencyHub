package com.example.CompetencyHub.common.exception;

/**
 * Thrown when a requested entity does not exist. Maps to HTTP 404.
 *
 * <p>Extends RuntimeException, not Exception, and that is a transaction decision as
 * much as a style one: Spring rolls back automatically on unchecked exceptions only.
 * A checked exception would commit the transaction unless every @Transactional method
 * remembered {@code rollbackFor}.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
