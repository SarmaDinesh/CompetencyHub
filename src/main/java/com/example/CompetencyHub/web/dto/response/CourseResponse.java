package com.example.CompetencyHub.web.dto.response;

import com.example.CompetencyHub.domain.model.Course;

/**
 * Course as returned by the API.
 *
 * <p>seatsAvailable is included so clients can show remaining capacity and disable an
 * enrol button before the user attempts it. `version` is deliberately NOT exposed —
 * it is an internal locking mechanism, not information any client should act on.
 */
public record CourseResponse(
        Long id,
        String code,
        String title,
        String description,
        int capacity,
        int seatsAvailable
) {
    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getCode(),
                course.getTitle(),
                course.getDescription(),
                course.getCapacity(),
                course.getSeatsAvailable()
        );
    }
}
