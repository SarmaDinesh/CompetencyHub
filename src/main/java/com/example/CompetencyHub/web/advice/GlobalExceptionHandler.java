package com.example.CompetencyHub.web.advice;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.common.exception.InvalidInputException;
import com.example.CompetencyHub.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates exceptions into RFC 9457 problem responses.
 *
 * <p>Every failure from this API now has the same shape, served as
 * {@code application/problem+json}:
 *
 * <pre>
 * {
 *   "type":     "https://competencyhub.example/errors/course-full",
 *   "title":    "Conflict",
 *   "status":   409,
 *   "detail":   "Course CS999 has no seats available",
 *   "instance": "/api/courses/11/enrollments",
 *   "timestamp":"2026-09-21T15:04:05Z"
 * }
 * </pre>
 *
 * <p><b>Old vs current.</b> The course slides build a custom {@code ErrorResponse} class
 * per project — which works, but means every API invents its own error shape and every
 * client writes bespoke parsing. Spring 6 added {@link ProblemDetail}, an implementation
 * of the RFC 9457 standard (formerly 7807). Same information, a shape clients and tools
 * already understand.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String ERROR_BASE = "https://competencyhub.example/errors/";

    /** 404: the request was valid, the resource does not exist. */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage(), "not-found");
    }

    /**
     * 409: a domain rule refused the request.
     *
     * <p>Not 400. The request is well formed — nothing the client could rewrite would
     * help, because the conflict is with the server's current state. That distinction
     * tells a client whether retrying could ever succeed.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage(),
                e.getClass().getSimpleName().replace("Exception", "").toLowerCase());
    }

    /**
     * 409, but transient: another transaction changed the row first. The same request
     * retried shortly may well succeed, which is worth saying explicitly.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT,
                "This resource was modified by another request. Please retry.",
                "concurrent-modification");
    }

    /**
     * 400: constraint violations on the request body.
     *
     * <p>Returns every invalid field at once rather than the first failure. A form that
     * rejects one field per round trip is a bad experience; the client should be able
     * to highlight all of them immediately.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(
                error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST,
                "One or more fields are invalid", "validation-failed");

        // Extension member — RFC 9457 allows additional properties beyond the standard ones.
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /** 400: the body was not parseable JSON, or a type did not match. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST,
                "Request body is missing or malformed JSON", "malformed-request");
    }

    /**
     * 400: values that are well formed but wrong for the target -- only the service can tell,
     * because only the service can see the target (e.g. a score outside THIS assessment's
     * range). Not 409: fixing the request would make it succeed.
     */
    @ExceptionHandler(InvalidInputException.class)
    public ProblemDetail handleInvalidInput(InvalidInputException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "invalid-input");
    }

    /**
     * 400: a path variable or query parameter that does not convert -- /api/courses/abc, or
     * ?status=PENDING for an enum that has no PENDING.
     *
     * <p>Before this handler existed, these fell through to handleUnexpected() below and came
     * back as 500. The catch-all Exception handler also catches Spring MVC's own client-error
     * exceptions, so each one you care about needs its own mapping. (Extending
     * ResponseEntityExceptionHandler is the alternative that maps all of them at once.)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST,
                "Invalid value '" + e.getValue() + "' for parameter '" + e.getName() + "'",
                "invalid-parameter");
    }

    /** 400: a required query parameter was left out, e.g. /api/notifications with no recipient. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException e) {
        return problem(HttpStatus.BAD_REQUEST,
                "Missing required parameter '" + e.getParameterName() + "'", "missing-parameter");
    }

    /**
     * 409: a database constraint refused the write -- the backstop behind every
     * check-then-act in the services. Two requests can both pass "is attempt 2 free?" and
     * only the UNIQUE constraint stops the second.
     *
     * <p>The message is deliberately generic. The exception text names the constraint and
     * often the SQL, which is useful in the log and an information leak in a response.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Constraint violation: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT,
                "The request conflicts with existing data. Please retry.", "data-conflict");
    }

    /**
     * 500: anything unhandled.
     *
     * <p>Logs the full stack trace server-side and returns a generic message. Exception
     * text can contain table names, SQL, and file paths — useful in a log, an
     * information leak in a response.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", "internal-error");
    }

    private ProblemDetail problem(HttpStatus status, String detail, String errorType) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(ERROR_BASE + errorType));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
