package com.example.CompetencyHub.web.dto.response;

import com.example.CompetencyHub.domain.model.Assessment;
import com.example.CompetencyHub.domain.model.ObjectiveAssessment;
import com.example.CompetencyHub.domain.model.PerformanceAssessment;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * An assessment as returned by the API. The mirror image of CreateAssessmentRequest: the
 * same annotations that READ the "type" field on the way in WRITE it on the way out, so a
 * client gets back the same shape it sent.
 *
 * <p>The alternative is one flat record with every field of every subtype, most of them
 * null. That works, but the client can no longer tell "this test has no word limit" from
 * "tests never have word limits" -- the shape stops describing the thing.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AssessmentResponse.Objective.class, name = "OBJECTIVE"),
        @JsonSubTypes.Type(value = AssessmentResponse.Performance.class, name = "PERFORMANCE")
})
public sealed interface AssessmentResponse {

    record Objective(Long id, Long competencyId, String title, int minScore, int maxScore,
                     String format, int questionCount, int passingScore)
            implements AssessmentResponse { }

    record Performance(Long id, Long competencyId, String title, int minScore, int maxScore,
                       String format, String rubricUrl, Integer wordLimit)
            implements AssessmentResponse { }

    /**
     * Entity to DTO. Pattern-matching switch again, but note the {@code default} branch that
     * the service's switch did not need.
     *
     * <p>Assessment is NOT sealed, and cannot be: Hibernate creates lazy-loading proxies by
     * generating a subclass of the entity at runtime, and a sealed class forbids exactly
     * that. So the compiler cannot prove this switch is exhaustive, and it asks for a
     * default.
     *
     * <p>That default is also a real case, not just a formality. A Hibernate proxy of
     * Assessment (what you get from submission.getAssessment() on a lazy association) is an
     * instance of neither subtype until unwrapped. Every entity handed to this method today
     * comes from a repository query, which returns real subtype instances -- but if a proxy
     * ever arrives, failing loudly beats silently mapping it wrong.
     */
    static AssessmentResponse from(Assessment assessment) {
        Long competencyId = assessment.getCompetency().getId();
        return switch (assessment) {
            case ObjectiveAssessment o -> new Objective(
                    o.getId(), competencyId, o.getTitle(),
                    o.getScoreRange().getMinScore(), o.getScoreRange().getMaxScore(),
                    o.describeFormat(), o.getQuestionCount(), o.getPassingScore());
            case PerformanceAssessment p -> new Performance(
                    p.getId(), competencyId, p.getTitle(),
                    p.getScoreRange().getMinScore(), p.getScoreRange().getMaxScore(),
                    p.describeFormat(), p.getRubricUrl(), p.getWordLimit());
            default -> throw new IllegalStateException(
                    "Unmapped assessment type: " + assessment.getClass().getName());
        };
    }
}
