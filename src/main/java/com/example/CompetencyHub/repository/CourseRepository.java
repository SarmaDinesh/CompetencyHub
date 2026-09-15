package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Course;

import java.util.List;
import java.util.Optional;

public interface CourseRepository {

    List<Course> findAll();

    Optional<Course> findById(Long id);

    Course save(Course course);
}
