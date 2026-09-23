package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.enums.EnrollmentStatus;
import com.example.CompetencyHub.domain.model.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentIdAndStatus(Long studentId, EnrollmentStatus status);

    /** Used in the next module to decide whether a course still has room. */
    long countByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    /** Guards against a student enrolling in the same course twice. */
    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);

    Page<Enrollment> findByStatus(EnrollmentStatus status, Pageable pageable);
}
