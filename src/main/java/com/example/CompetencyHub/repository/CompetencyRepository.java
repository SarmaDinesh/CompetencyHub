package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Competency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompetencyRepository extends JpaRepository<Competency, Long> {
    /**
     * Traverses the association by property path: course → id. Spring Data resolves
     * "CourseId" to competency.course.id without you naming the foreign key column.
     */
    List<Competency> findByCourseIdOrderByOrderIndexAsc(Long courseId);

    long countByCourseId(Long courseId);
}
