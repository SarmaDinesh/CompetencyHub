package com.example.CompetencyHub.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "submission")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY on both, per the Module 5 rule: @ManyToOne defaults to EAGER, which turns any query
    // returning submissions into a join you did not ask for.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @Column(nullable = false)
    private int score;

    // LocalDateTime, not Instant -- the column is `timestamp without time zone`, matching the
    // rest of your schema. (job_run uses TIMESTAMPTZ because I wrote it without checking. Worth
    // standardizing on timestamptz across the board eventually; noted as a gap, not churned now.)
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    protected Submission() { }

    public Submission(Student student, Assessment assessment, int score) {
        this.student = student;
        this.assessment = assessment;
        this.score = score;
        this.submittedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public Assessment getAssessment() { return assessment; }
    public int getScore() { return score; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
}
