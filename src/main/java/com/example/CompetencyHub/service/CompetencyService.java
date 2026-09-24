package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.model.Competency;

import java.util.List;

public interface CompetencyService {

    /** @throws com.example.CompetencyHub.common.exception.NotFoundException if the course does not exist */
    List<Competency> findByCourse(Long courseId);

    Competency findById(Long id);

    /** {@code orderIndex} may be null, meaning "after the last one". */
    Competency create(Long courseId, String title, int weight, Integer orderIndex);

    Competency update(Long id, String title, int weight, int orderIndex);

    void delete(Long id);
}
