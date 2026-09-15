package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.model.Course;

import java.util.List;
import java.util.Optional;

public interface CourseService {
    List<Course> findAll();

    Course findById(Long id);

    Course create(Course course);
}
