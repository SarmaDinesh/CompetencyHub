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

    /** Required by JPA. Protected because only subclasses should call it. */
    protected Assessment() {
    }

    protected Assessment(Competency competency, String title, ScoreRange scoreRange) {
        this.competency = competency;
        this.title = title;
        this.scoreRange = scoreRange;
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

    // Note: no equals/hashCode override. Entities are identified by id, and an
    // unsaved entity has none yet — so field-based equality would break the moment
    // an object is persisted. Default reference equality is the safe choice here.
}
