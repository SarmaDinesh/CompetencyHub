package com.example.CompetencyHub.domain.model;

import com.example.CompetencyHub.domain.enums.AttemptOutcome;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * One recorded enrollment attempt, successful or not.
 *
 * <p>Stores raw ids rather than @ManyToOne associations. An audit row is a historical
 * fact, not a live link — it must remain readable after the student or course it
 * mentions has been deleted, and it should never cascade or lazy-load anything.
 */
@Entity
@Table(name = "enrollment_attempt")
public class EnrollmentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AttemptOutcome outcome;

    /** Why it was rejected, when it was. Null on success. */
    @Column(length = 500)
    private String detail;

    protected EnrollmentAttempt() {
    }

    public EnrollmentAttempt(Long studentId, Long courseId, AttemptOutcome outcome, String detail) {
        this.studentId = studentId;
        this.courseId = courseId;
        this.outcome = outcome;
        this.detail = detail;
        this.attemptedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public Long getCourseId() { return courseId; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public AttemptOutcome getOutcome() { return outcome; }
    public String getDetail() { return detail; }
}
