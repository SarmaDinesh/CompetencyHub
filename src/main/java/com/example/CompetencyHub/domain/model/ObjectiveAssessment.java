package com.example.CompetencyHub.domain.model;

import com.example.CompetencyHub.domain.embeddable.ScoreRange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/**
 * A fixed-question test — multiple choice, short answer — scored automatically.
 *
 * <p>Its table holds only the columns unique to this subtype. Everything shared
 * (title, competency, score range) lives in the parent `assessment` row.
 */
@Entity
@Table(name = "objective_assessment")
/*
 * Tells Hibernate this table's primary key doubles as the foreign key to the parent
 * table. Optional when the column is already named "id" — written explicitly here
 * because it's the mechanism that makes JOINED inheritance work, and hiding it
 * behind a default makes the mapping harder to read.
 */
@PrimaryKeyJoinColumn(name = "id")
public class ObjectiveAssessment extends Assessment {

    /** Primitive int: the column is NOT NULL, so null is not a possible value. */
    @Column(name = "question_count", nullable = false)
    private int questionCount;

    /** Required by JPA. */
    protected ObjectiveAssessment() {
    }

    public ObjectiveAssessment(Competency competency, String title, ScoreRange scoreRange,
                               int questionCount, int passingScore) {
        // passingScore now belongs to the parent (V9), so it goes up through super().
        super(competency, title, scoreRange, passingScore);
        this.questionCount = questionCount;
    }

    @Override
    public String describeFormat() {
        return questionCount + "-question test, pass at " + getPassingScore();
    }

    public int getQuestionCount() {
        return questionCount;
    }
}