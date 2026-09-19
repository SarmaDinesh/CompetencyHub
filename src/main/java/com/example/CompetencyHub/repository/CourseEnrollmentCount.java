package com.example.CompetencyHub.repository;

/**
 * Projection for the enrollment-count report.
 *
 * <p>Spring Data implements this interface at runtime, backed by the query's result
 * columns. Selecting four columns instead of hydrating full Course entities keeps the
 * query cheap and makes the contract explicit: this is a report row, and it will never
 * accidentally be saved back.
 */
public interface CourseEnrollmentCount {
    Long getId();
    String getCode();
    String getTitle();
    Long getEnrolled();
}
