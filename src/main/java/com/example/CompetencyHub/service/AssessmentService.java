package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.model.Assessment;
import com.example.CompetencyHub.domain.model.ObjectiveAssessment;
import com.example.CompetencyHub.domain.model.PerformanceAssessment;

import java.util.List;

/**
 * One create method per subtype, taking plain values -- not the web layer's request records.
 *
 * <p>The service must not import anything from {@code web}. Dependencies point one way
 * (web -> service -> domain); a service that accepts an HTTP DTO can only be called by
 * code that speaks HTTP. A Kafka consumer or a CSV import would have to fake a web request
 * to create an assessment.
 */
public interface AssessmentService {

    List<Assessment> findByCompetency(Long competencyId);

    Assessment findById(Long id);

    ObjectiveAssessment createObjective(Long competencyId, String title, int minScore, int maxScore,
                                        int questionCount, int passingScore);

    PerformanceAssessment createPerformance(Long competencyId, String title, int minScore, int maxScore,
                                            int passingScore, String rubricUrl, Integer wordLimit);

    void delete(Long id);
}
