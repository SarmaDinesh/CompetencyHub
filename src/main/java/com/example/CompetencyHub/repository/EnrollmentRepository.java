package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.enums.EnrollmentStatus;
import com.example.CompetencyHub.domain.model.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentIdAndStatus(Long studentId, EnrollmentStatus status);

    /** Used in the next module to decide whether a course still has room. */
    long countByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    /**
     * Finds an enrollment for this student and course that blocks a new one.
     *
     * <p>Replaces the old {@code existsByStudentIdAndCourseId}, which counted WITHDRAWN rows
     * too -- so a student who dropped a course could never take it again.
     *
     * <p>Spring Data reads the name as:
     * {@code WHERE student.id = ? AND course.id = ? AND status IN (?) LIMIT 1}.
     * Returning the row (not a boolean) lets the service say WHY it refused: "already
     * enrolled" and "already completed" are different messages to a student.
     */
    Optional<Enrollment> findFirstByStudentIdAndCourseIdAndStatusIn(
            Long studentId, Long courseId, Collection<EnrollmentStatus> statuses);

    Page<Enrollment> findByStatus(EnrollmentStatus status, Pageable pageable);
}
