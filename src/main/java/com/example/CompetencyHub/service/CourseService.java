package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.model.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CourseService {
    /** @throws com.example.CompetencyHub.common.exception.NotFoundException if no such course */
    Course findById(Long id);

    Page<Course> findAll(Pageable pageable);

    List<Course> search(String titleFragment);

    Course create(String code, String title, String description, Integer capacity);

    Course update(Long id, String title, String description, int capacity);

    void delete(Long id);
}
