package com.example.CompetencyHub.web.dto.response;

import com.example.CompetencyHub.domain.model.Enrollment;

import java.time.Instant;

public record EnrollmentResponse(
        Long id,
        Long studentId,
        Long courseId,
        String courseCode,
        Instant enrolledAt,
        String status,
        int seatsRemaining
) {
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getStudent().getId(),
                enrollment.getCourse().getId(),
                enrollment.getCourse().getCode(),
                enrollment.getEnrolledAt(),
                enrollment.getStatus().name(),
                enrollment.getCourse().getSeatsAvailable()
        );
    }
}
