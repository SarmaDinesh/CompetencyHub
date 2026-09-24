package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.enums.SubmissionStatus;
import com.example.CompetencyHub.domain.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * Competencies in a course this student has mastered: at least one GRADED submission at
     * or above the assessment's passing score.
     *
     * <p>Two changes in V9 made this simpler and more correct at once:
     * <ul>
     *   <li>passing_score moved to the parent {@code assessment} table, so the join to
     *       objective_assessment is gone -- and performance tasks now count toward mastery.</li>
     *   <li>{@code status = 'GRADED'} is required. An ungraded performance task has a NULL
     *       score; {@code NULL >= 70} is NULL in SQL, which already filters it, but relying on
     *       three-valued logic is how the next reader introduces a bug. Say what you mean.</li>
     * </ul>
     *
     * <p>Still native SQL, for the reason in the original comment: DISTINCT over a column two
     * joins away reads more plainly here than in JPQL.
     */
    @Query(value = """
            SELECT count(DISTINCT a.competency_id)
            FROM submission s
            JOIN assessment a  ON a.id = s.assessment_id
            JOIN competency c  ON c.id = a.competency_id
            WHERE s.student_id = :studentId
              AND c.course_id = :courseId
              AND s.status = 'GRADED'
              AND s.score >= a.passing_score
            """, nativeQuery = true)
    long countMasteredCompetencies(@Param("studentId") Long studentId,
                                   @Param("courseId") Long courseId);

    /** Guards assessment deletion: graded work must not disappear with its assessment. */
    boolean existsByAssessmentId(Long assessmentId);

    /**
     * Same guard one level up. The derived name walks two associations:
     * submission.assessment.competency.id -- Spring Data writes the joins.
     */
    boolean existsByAssessmentCompetencyId(Long competencyId);

    /** How many attempts this student has made, so the next one gets the next number. */
    long countByStudentIdAndAssessmentId(Long studentId, Long assessmentId);

    List<Submission> findByStudentIdOrderBySubmittedAtDesc(Long studentId);

    /** The whole history for an assessment, oldest first. */
    List<Submission> findByAssessmentIdOrderBySubmittedAtAsc(Long assessmentId);

    /**
     * The grading queue when status = SUBMITTED. Oldest first: first in, first graded.
     * Served by the (assessment_id, status) index V9 adds.
     */
    List<Submission> findByAssessmentIdAndStatusOrderBySubmittedAtAsc(Long assessmentId,
                                                                     SubmissionStatus status);
}
