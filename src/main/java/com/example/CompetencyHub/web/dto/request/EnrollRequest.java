package com.example.CompetencyHub.web.dto.request;

/** Body of POST /api/courses/{courseId}/enrollments. */
public record EnrollRequest(Long studentId) {
}
