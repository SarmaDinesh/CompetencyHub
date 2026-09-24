package com.example.CompetencyHub.domain.model;

import com.example.CompetencyHub.domain.embeddable.ScoreRange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/**
 * An open-ended task — an essay, a project, a presentation — graded by a mentor
 * against a rubric rather than scored automatically.
 */
@Entity
@Table(name = "performance_assessment")
@PrimaryKeyJoinColumn(name = "id")
public class PerformanceAssessment extends Assessment {

    /** Link to the published grading rubric. Nullable: not every task has one. */
    @Column(name = "rubric_url", length = 500)
    private String rubricUrl;

    /**
     * Integer, not int — and this is the point worth remembering.
     *
     * The column is nullable, meaning "no word limit". A primitive int cannot hold
     * null, so Hibernate would quietly store 0 instead, which reads as "limit of
     * zero words". Every nullable numeric column needs the wrapper type.
     */
    @Column(name = "word_limit")
    private Integer wordLimit;

    /** Required by JPA. */
    protected PerformanceAssessment() {
    }

    public PerformanceAssessment(Competency competency, String title, ScoreRange scoreRange,
                                 int passingScore, String rubricUrl, Integer wordLimit) {
        super(competency, title, scoreRange, passingScore);
        this.rubricUrl = rubricUrl;
        this.wordLimit = wordLimit;
    }

    @Override
    public String describeFormat() {
        return wordLimit == null
                ? "Performance task, graded by rubric, pass at " + getPassingScore()
                : "Performance task, up to " + wordLimit + " words, pass at " + getPassingScore();
    }

    public String getRubricUrl() {
        return rubricUrl;
    }

    public Integer getWordLimit() {
        return wordLimit;
    }

    /** True when there is no limit, or the text fits inside it. */
    public boolean fitsWordLimit(String text) {
        if (wordLimit == null || text == null) return true;
        String trimmed = text.strip();
        int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
        return words <= wordLimit;
    }
}
