package com.example.CompetencyHub.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;

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

    // BigDecimal, because the column is numeric(5,2). Rounding to an int here would throw away
    // the two decimal places the schema deliberately reserves -- 66.67% would become 67%, and
    // the difference is visible to a student looking at their own progress bar.
    @Column(name = "percent_complete", nullable = false)
    private BigDecimal percentComplete;

    @Column(name = "recalculated_at", nullable = false)
    private LocalDateTime recalculatedAt;

    protected Progress() { }

    public Progress(Long studentId, Long courseId) {
        this.id = new ProgressId(studentId, courseId);
        this.competenciesMastered = 0;
        this.percentComplete = BigDecimal.ZERO;
        this.recalculatedAt = LocalDateTime.now();
    }

    /**
     * Returns true when something actually changed, so the caller can count real updates
     * rather than rows visited.
     */
    public boolean recalculate(long mastered, long totalCompetencies) {
        BigDecimal newPercent = totalCompetencies == 0
                // A course with no competencies yet. 0.00 is the honest answer; dividing would
                // be an ArithmeticException in an unattended job.
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(mastered * 100.0)
                .divide(BigDecimal.valueOf(totalCompetencies), 2, RoundingMode.HALF_UP);

        boolean changed = this.competenciesMastered != (int) mastered
                || this.percentComplete.compareTo(newPercent) != 0;

        this.competenciesMastered = (int) mastered;
        this.percentComplete = newPercent;
        // Stamped on every run, changed or not -- "when did we last verify this" is a different
        // and more useful question than "when did this last change".
        this.recalculatedAt = LocalDateTime.now();
        return changed;
    }

    public ProgressId getId() { return id; }
    public int getCompetenciesMastered() { return competenciesMastered; }
    public BigDecimal getPercentComplete() { return percentComplete; }
    public LocalDateTime getRecalculatedAt() { return recalculatedAt; }
}
