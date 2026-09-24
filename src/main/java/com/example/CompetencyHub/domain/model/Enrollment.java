package com.example.CompetencyHub.domain.model;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.domain.enums.EnrollmentStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
/*
 * No @UniqueConstraint here any more. The old (student_id, course_id) constraint made
 * re-enrollment after a withdrawal impossible. V8 replaces it with a PARTIAL unique index
 * (only ACTIVE rows), which JPA annotations cannot express -- so the rule now lives in the
 * migration alone. With ddl-auto=validate, Flyway owns the schema anyway; the annotation
 * was documentation, and documentation that disagrees with the database is worse than none.
 */
@Table(name = "enrollment")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status;

    protected Enrollment() {
    }

    public Enrollment(Student student, Course course) {
        this.student = student;
        this.course = course;
        this.enrolledAt = Instant.now();
        this.status = EnrollmentStatus.ACTIVE;
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public Course getCourse() { return course; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public EnrollmentStatus getStatus() { return status; }

    /**
     * Moves this enrollment from ACTIVE to WITHDRAWN.
     *
     * <p>Guarded on the entity, same rule as {@link Course#reserveSeat()}: the object that
     * owns the status decides which transitions are legal. Without the guard, withdrawing
     * twice was "successful" twice, and the service handed back a seat each time -- a
     * course with 30 seats could end up advertising 31.
     *
     * <p>Only ACTIVE can be withdrawn. WITHDRAWN -> WITHDRAWN is a repeat, and
     * COMPLETED -> WITHDRAWN would rewrite history (the student finished the course).
     *
     * @throws BusinessRuleException if the enrollment is not ACTIVE
     */
    public void withdraw() {
        if (status != EnrollmentStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Enrollment " + id + " cannot be withdrawn because it is " + status);
        }
        this.status = EnrollmentStatus.WITHDRAWN;
    }

    public boolean isActive() {
        return status == EnrollmentStatus.ACTIVE;
    }
}
