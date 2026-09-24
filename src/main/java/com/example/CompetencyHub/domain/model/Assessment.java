package com.example.CompetencyHub.domain.model;

import com.example.CompetencyHub.domain.embeddable.ScoreRange;
import jakarta.persistence.*;

/**
 * Base type for anything a student completes to prove a competency.
 *
 * <p>Abstract on purpose: there is no such thing as a generic "assessment" in the
 * domain — every one is either an objective test or a performance task. Making the
 * class abstract means the type system enforces that too.
 *
 * <p>{@code InheritanceType.JOINED}: shared columns here, subtype columns in their
 * own tables, linked by a shared primary key. Costs one join per read; buys real
 * NOT NULL constraints on subtype columns.
 *
 * <p>Queries against this class are polymorphic — {@code findAll()} returns a mix of
 * ObjectiveAssessment and PerformanceAssessment instances, each as its real type.
 */
@Entity
@Table(name = "assessment")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Assessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // Postgres BIGSERIAL assigns the id
    private Long id;

    /**
     * LAZY is deliberate. JPA defaults @ManyToOne to EAGER, which would load the
     * competency — and through it the course — on every assessment read, whether or
     * not anyone asked for them. Always set LAZY on @ManyToOne and fetch explicitly
     * when needed (see the join-fetch queries in the next module).
     *
     * optional = false mirrors the NOT NULL in the schema, so Hibernate can skip
     * an outer join and validate earlier.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competency_id", nullable = false)
    private Competency competency;

    @Column(nullable = false, length = 200)
    private String title;

    /** min_score and max_score, grouped into one value object. Not a separate table. */
    @Embedded
    private ScoreRange scoreRange;

    /**
     * Minimum score that proves the competency. Lives here, not on a subtype, since V9:
     * once performance tasks needed a pass mark too, it became common to every assessment,
     * and common state belongs in the parent. "Pull up field" -- the entity mirrors the
     * column that moved from objective_assessment to assessment.
     */
    @Column(name = "passing_score", nullable = false)
    private int passingScore;

    /** Required by JPA. Protected because only subclasses should call it. */
    protected Assessment() {
    }

    protected Assessment(Competency competency, String title, ScoreRange scoreRange, int passingScore) {
        // Same fail-at-construction rule as ScoreRange: an assessment whose pass mark cannot
        // be reached (or cannot be missed) never exists. The API validates this first so the
        // client gets a 400; this is the guarantee for every other caller.
        if (!scoreRange.contains(passingScore)) {
            throw new IllegalArgumentException("passingScore " + passingScore
                    + " is outside the range " + scoreRange.getMinScore() + "-" + scoreRange.getMaxScore());
        }
        this.competency = competency;
        this.title = title;
        this.scoreRange = scoreRange;
        this.passingScore = passingScore;
    }

    /** Whether a score is good enough to master the competency. */
    public boolean isPassing(int score) {
        return score >= passingScore;
    }

    /**
     * Human-readable description of how this assessment is completed.
     *
     * <p>Abstract rather than a field: the answer is derived from each subtype's own
     * data, so it belongs with that data. Callers can describe any assessment without
     * knowing or testing its concrete type — polymorphism doing the work an
     * if/else chain would otherwise do.
     */
    public abstract String describeFormat();

    public Long getId() {
        return id;
    }

    public Competency getCompetency() {
        return competency;
    }

    public String getTitle() {
        return title;
    }

    public ScoreRange getScoreRange() {
        return scoreRange;
    }

    public int getPassingScore() {
        return passingScore;
    }

    // Note: no equals/hashCode override. Entities are identified by id, and an
    // unsaved entity has none yet — so field-based equality would break the moment
    // an object is persisted. Default reference equality is the safe choice here.
}
