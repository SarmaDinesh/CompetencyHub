package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findAll();

    Optional<Course> findById(Long id);

    Course save(Course course);
}
