package com.example.CompetencyHub.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * How far one student has got in one course.
 *
 * <p>A derived rollup, not a source of truth: the authoritative data is the set of
 * graded submissions. This row is recomputed from them by a scheduled job (added in
 * a later module) so that reading a student's progress is a single-row lookup rather
 * than an aggregation over every submission they have ever made.
 */
@Entity
@Table(name = "progress")
public class Progress {

    /**
     * {@code @EmbeddedId} maps the composite key as a single embedded object.
     *
     * <p>The alternative, {@code @IdClass}, declares the key fields directly on the
     * entity and names a separate class holding copies of them. @EmbeddedId keeps the
     * key in one place, which is why it is the more common choice today — but the
     * exam may show either, so recognise both.
     */
    @EmbeddedId
    private ProgressId id;

    /** Count of competencies in this course the student has now proven. */
    @Column(name = "competencies_mastered", nullable = false)
    private int competenciesMastered;

    /**
     * BigDecimal, never double.
     *
     * Binary floating point cannot represent most decimal fractions exactly —
     * 0.1 + 0.2 evaluates to 0.30000000000000004. Harmless in a simulation, wrong for
     * a number a person reads or a system compares for equality. Percentages, money,
     * and scores all use BigDecimal. precision=5, scale=2 allows 0.00 through 999.99.
     */
    @Column(name = "percent_complete", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentComplete;

    /**
     * When this rollup was last recomputed — so a stale row is visibly stale rather
     * than silently wrong.
     *
     * Instant, not LocalDateTime: this is a point on the global timeline, and Instant
     * carries no timezone ambiguity. Use LocalDateTime only for wall-clock values
     * that genuinely have no timezone, such as a recurring 09:00 class start.
     */
    @Column(name = "recalculated_at", nullable = false)
    private Instant recalculatedAt;

    /** Required by JPA. */
    protected Progress() {
    }

    /** Starts a student at zero progress in a course. */
    public Progress(Long studentId, Long courseId) {
        this.id = new ProgressId(studentId, courseId);
        this.competenciesMastered = 0;
        this.percentComplete = BigDecimal.ZERO;
        this.recalculatedAt = Instant.now();
    }

    /**
     * Replaces the rollup with freshly computed values.
     *
     * <p>One method rather than three setters, because these fields only ever change
     * together — a percentage that disagrees with the mastered count is a bug, and
     * this signature makes that state unreachable.
     */
    public void update(int mastered, BigDecimal percent) {
        this.competenciesMastered = mastered;
        this.percentComplete = percent;
        this.recalculatedAt = Instant.now();
    }

    public ProgressId getId() {
        return id;
    }

    public int getCompetenciesMastered() {
        return competenciesMastered;
    }

    public BigDecimal getPercentComplete() {
        return percentComplete;
    }

    public Instant getRecalculatedAt() {
        return recalculatedAt;
    }
}
