package com.example.CompetencyHub.web.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Body of POST /api/competencies/{id}/assessments -- ONE endpoint, TWO shapes.
 *
 * <p>The client says which shape it is sending with a {@code "type"} field:
 *
 * <pre>
 * { "type": "OBJECTIVE",   "title": "Quiz 1", "minScore": 0, "maxScore": 100,
 *   "questionCount": 20, "passingScore": 70 }
 *
 * { "type": "PERFORMANCE", "title": "Essay",  "minScore": 0, "maxScore": 100,
 *   "rubricUrl": "https://...", "wordLimit": 1500 }
 * </pre>
 *
 * <p><b>How it works.</b> {@code @JsonTypeInfo} tells Jackson "read the {@code type}
 * property first, and use it to pick the class". {@code @JsonSubTypes} is the lookup table
 * from that value to a record. An unknown or missing type fails deserialization, which the
 * existing handler already turns into a 400.
 *
 * <p><b>Why {@code sealed}.</b> {@code permits} lists every implementation, and the
 * compiler holds you to it. The controller's {@code switch} over this type then needs no
 * {@code default} branch -- and if a third assessment type is added here, every switch that
 * forgot to handle it stops compiling. The error moves from runtime to your IDE.
 *
 * <p><b>Old vs new.</b> Before Java 17 you would write a plain interface and dispatch with
 * an {@code if (request instanceof X) ... else if ...} chain, cast by hand, and end with an
 * {@code else throw} for the case you hoped never happened. Sealed types plus pattern
 * matching for switch (Java 21) replace all three.
 *
 * <p>Annotations still live in {@code com.fasterxml.jackson.annotation}, even on Boot 4's
 * Jackson 3: the core moved to the {@code tools.jackson} package, but the annotations
 * module kept its old name so existing code keeps compiling.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CreateObjectiveAssessmentRequest.class, name = "OBJECTIVE"),
        @JsonSubTypes.Type(value = CreatePerformanceAssessmentRequest.class, name = "PERFORMANCE")
})
public sealed interface CreateAssessmentRequest
        permits CreateObjectiveAssessmentRequest, CreatePerformanceAssessmentRequest {

    String title();
    Integer minScore();
    Integer maxScore();
}
