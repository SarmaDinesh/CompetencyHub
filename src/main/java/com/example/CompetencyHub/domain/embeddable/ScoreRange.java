package com.example.CompetencyHub.domain.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public class ScoreRange {
    @Column(name = "min_score", nullable = false)
    private int minScore;

    @Column(name = "max_score", nullable = false)
    private int maxScore;

    /**
     * Required by JPA, which instantiates embeddables reflectively.
     * Protected rather than public so application code must use the validating
     * constructor below.
     */
    protected ScoreRange() {
    }

    public ScoreRange(int minScore, int maxScore) {
        // Fail at construction, not at use. An invalid ScoreRange can never exist,
        // so nothing downstream needs to defend against one.
        if (minScore >= maxScore) {
            throw new IllegalArgumentException(
                    "minScore (" + minScore + ") must be less than maxScore (" + maxScore + ")");
        }
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    /** True if the given score falls inside this range, bounds included. */
    public boolean contains(int score) {
        return score >= minScore && score <= maxScore;
    }

    /** Width of the range, useful when normalising scores to a percentage. */
    public int span() {
        return maxScore - minScore;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    // Value semantics: equality is by value, not by reference or id.
    // Hibernate relies on this to detect whether an embedded value actually changed.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScoreRange other)) return false;   // pattern matching, Java 16+
        return minScore == other.minScore && maxScore == other.maxScore;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minScore, maxScore);
    }
}
