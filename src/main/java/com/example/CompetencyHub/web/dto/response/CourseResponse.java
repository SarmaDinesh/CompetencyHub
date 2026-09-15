package com.example.CompetencyHub.web.dto.response;

import com.example.CompetencyHub.domain.model.Course;

public record CourseResponse(
        Long id,
        String code,
        String title,
        String description,
        int capacity
) {
    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getCode(),
                course.getTitle(),
                course.getDescription(),
                course.getCapacity()
        );
    }
}
