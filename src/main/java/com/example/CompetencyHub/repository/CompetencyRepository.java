package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Competency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CompetencyRepository extends JpaRepository<Competency, Long> {
    /**
     * Traverses the association by property path: course → id. Spring Data resolves
     * "CourseId" to competency.course.id without you naming the foreign key column.
     */
    List<Competency> findByCourseIdOrderByOrderIndexAsc(Long courseId);

    long countByCourseId(Long courseId);

    /**
     * Highest position used in a course, or 0 for a course with none.
     *
     * <p>coalesce() matters: max() over zero rows is NULL, not 0, and NULL cannot unbox into
     * the int return type -- you would get an exception on the first competency of every
     * new course, which is exactly the case nobody tests by hand.
     */
    @Query("select coalesce(max(c.orderIndex), 0) from Competency c where c.course.id = :courseId")
    int findMaxOrderIndexByCourseId(@Param("courseId") Long courseId);
}
