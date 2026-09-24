package com.example.CompetencyHub.domain.model;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.common.exception.InvalidInputException;
import com.example.CompetencyHub.domain.enums.SubmissionStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One attempt by one student at one assessment.
 *
 * <p><b>A state machine in an entity.</b> A submission is SUBMITTED, then GRADED, and never
 * goes back. There are no setters for status, score, or grader: the only way to change them
 * is {@link #grade}, which checks the transition is legal first. The same rule as
 * Course.reserveSeat() and Enrollment.withdraw() -- the object that owns the state decides
 * how it may change -- applied to a lifecycle rather than a counter.
 *
 * <p>Real-world picture: an exam script. Handed in, it goes on the marking pile; once marked,
 * the grade is written in ink. Marking it again is not a correction, it is a second opinion
 * on the same paper, and that needs a different process.
 */
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

    /** 1 for the first try at this assessment, 2 for the second... Unique per pair (V9). */
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    /**
     * Integer, not int: null until graded. A primitive would store 0, and "scored zero" is a
     * real, different fact from "not marked yet".
     */
    @Column
    private Integer score;

    /** The student's answer for a performance task. Null for objective tests. */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** Mentor's comments. Optional, and never set on an auto-graded test. */
    @Column(columnDefinition = "TEXT")
    private String feedback;

    // LocalDateTime, not Instant -- the column is `timestamp without time zone`, matching the
    // rest of your schema. (job_run uses TIMESTAMPTZ because I wrote it without checking. Worth
    // standardizing on timestamptz across the board eventually; noted as a gap, not churned now.)
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    /** Who graded it. Null for an auto-graded objective test -- no person was involved. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private Mentor gradedBy;

    /**
     * Second use of @Version in the project, protecting a different race. Course.version stops
     * two students taking the last seat; this stops two mentors grading the same submission at
     * the same moment. Both read SUBMITTED, both call grade(), both pass the status check in
     * memory -- and the second UPDATE finds the version already moved and fails with a 409.
     * The status check alone cannot catch that, because each transaction checked a copy.
     */
    @Version
    @Column(nullable = false)
    private long version;

    protected Submission() { }

    /** A new, ungraded attempt. */
    public Submission(Student student, Assessment assessment, int attemptNumber, String content) {
        this.student = student;
        this.assessment = assessment;
        this.attemptNumber = attemptNumber;
        this.content = content;
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedAt = LocalDateTime.now();
    }

    /**
     * SUBMITTED -> GRADED. The one legal transition, and the only way to set a score.
     *
     * @param mentor null for an automatically graded objective test
     * @throws BusinessRuleException  if already graded (409: the state forbids it)
     * @throws InvalidInputException  if the score is outside the assessment's range (400)
     */
    public void grade(int score, String feedback, Mentor mentor) {
        if (status != SubmissionStatus.SUBMITTED) {
            throw new BusinessRuleException("Submission " + id + " is already " + status);
        }
        if (!assessment.getScoreRange().contains(score)) {
            throw new InvalidInputException("Score " + score + " is outside the range "
                    + assessment.getScoreRange().getMinScore() + "-"
                    + assessment.getScoreRange().getMaxScore());
        }
        this.score = score;
        this.feedback = feedback;
        this.gradedBy = mentor;
        this.gradedAt = LocalDateTime.now();
        this.status = SubmissionStatus.GRADED;
    }

    public boolean isGraded() {
        return status == SubmissionStatus.GRADED;
    }

    /** Only meaningful once graded. */
    public boolean isPassing() {
        return isGraded() && assessment.isPassing(score);
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public Assessment getAssessment() { return assessment; }
    public int getAttemptNumber() { return attemptNumber; }
    public SubmissionStatus getStatus() { return status; }
    public Integer getScore() { return score; }
    public String getContent() { return content; }
    public String getFeedback() { return feedback; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getGradedAt() { return gradedAt; }
    public Mentor getGradedBy() { return gradedBy; }
}
