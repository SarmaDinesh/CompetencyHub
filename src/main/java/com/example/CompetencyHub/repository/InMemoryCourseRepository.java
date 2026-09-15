package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Course;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryCourseRepository implements CourseRepository{

    private final Map<Long, Course> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public List<Course> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public Optional<Course> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Course save(Course course) {
        if (course.getId() == null) {
            course.setId(sequence.incrementAndGet());
        }
        store.put(course.getId(), course);
        return course;
    }
}
