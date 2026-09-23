package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Progress;
import com.example.CompetencyHub.domain.model.ProgressId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for per-student, per-course progress rollups.
 *
 * <p>The second type parameter is ProgressId, not Long — the id type must match the
 * entity's actual key, so findById() and deleteById() take a ProgressId:
 *
 * <pre>{@code
 * progressRepository.findById(new ProgressId(studentId, courseId));
 * }</pre>
 */
public interface ProgressRepository extends JpaRepository<Progress, ProgressId> {
}
