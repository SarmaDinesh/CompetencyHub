package com.example.CompetencyHub.service;

import com.example.CompetencyHub.config.AppProperties;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CourseServiceImpl implements CourseService{

    private final CourseRepository courseRepository;
    private final AppProperties properties;

    public CourseServiceImpl(CourseRepository courseRepository, AppProperties properties) {
        this.courseRepository = courseRepository;
        this.properties = properties;
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
    @Transactional
    public Course create(Course course) {
        if (course.getCapacity() <= 0) {
            course.setCapacity(properties.getDefaultCourseCapacity());
        }
        return courseRepository.save(course);
    }
}
