package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * Native SQL here, and the reason matters: every column below was read directly out of
     * information_schema, so these names are verified fact, not assumption. What I have NOT
     * verified is the Java field names on Assessment -- which is precisely what JPQL resolves
     * against, and precisely what just failed.
     *
     * So the usual preference flips for this one query. JPQL is better when the schema is the
     * unknown; native is better when the schema is the only thing known. Convert this back to
     * JPQL once the entity is confirmed -- see below.
     */
    @Query(value = """
            SELECT count(DISTINCT a.competency_id)
            FROM submission s
            JOIN assessment a  ON a.id = s.assessment_id
            JOIN competency c  ON c.id = a.competency_id
            JOIN objective_assessment oa ON oa.id = a.id
            WHERE s.student_id = :studentId
              AND c.course_id = :courseId
              AND s.score >= oa.passing_score
            """, nativeQuery = true)
    long countMasteredCompetencies(@Param("studentId") Long studentId,
                                   @Param("courseId") Long courseId);
}
