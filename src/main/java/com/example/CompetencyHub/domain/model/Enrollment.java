package com.example.CompetencyHub.domain.model;

import com.example.CompetencyHub.domain.enums.EnrollmentStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "enrollment",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_enrollment_student_course",
                columnNames = {"student_id", "course_id"}
        )
)
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

    public void withdraw() {
        this.status = EnrollmentStatus.WITHDRAWN;
    }
}
