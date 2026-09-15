package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.repository.CourseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CourseServiceImpl implements CourseService{

    private final CourseRepository courseRepository;

    public CourseServiceImpl(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public List<Course> findAll() {
        return courseRepository.findAll();
    }

    @Override
    public Course findById(Long id) {
        return courseRepository.findById(id).
                orElseThrow(() -> new NoSuchElementException("Course not found: "+id));
    }

    @Override
    public Course create(Course course) {
        if (course.getCapacity() <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        return courseRepository.save(course);
    }
}
