package com.example.CompetencyHub.service.impl;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.common.exception.NotFoundException;
import com.example.CompetencyHub.domain.embeddable.ScoreRange;
import com.example.CompetencyHub.domain.model.Assessment;
import com.example.CompetencyHub.domain.model.Competency;
import com.example.CompetencyHub.domain.model.ObjectiveAssessment;
import com.example.CompetencyHub.domain.model.PerformanceAssessment;
import com.example.CompetencyHub.repository.AssessmentRepository;
import com.example.CompetencyHub.repository.CompetencyRepository;
import com.example.CompetencyHub.repository.SubmissionRepository;
import com.example.CompetencyHub.service.AssessmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssessmentServiceImpl implements AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final CompetencyRepository competencyRepository;
    private final SubmissionRepository submissionRepository;

    public AssessmentServiceImpl(AssessmentRepository assessmentRepository,
                                 CompetencyRepository competencyRepository,
                                 SubmissionRepository submissionRepository) {
        this.assessmentRepository = assessmentRepository;
        this.competencyRepository = competencyRepository;
        this.submissionRepository = submissionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Assessment> findByCompetency(Long competencyId) {
        if (!competencyRepository.existsById(competencyId)) {
            throw new NotFoundException("Competency not found: " + competencyId);
        }
        return assessmentRepository.findByCompetencyIdOrderByIdAsc(competencyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Assessment findById(Long id) {
        return assessmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Assessment not found: " + id));
    }

    @Override
    @Transactional
    public ObjectiveAssessment createObjective(Long competencyId, String title, int minScore,
                                               int maxScore, int questionCount, int passingScore) {
        Competency competency = loadCompetency(competencyId);
        ObjectiveAssessment assessment = new ObjectiveAssessment(
                competency, title, new ScoreRange(minScore, maxScore), questionCount, passingScore);

        // JOINED inheritance at work: this one save() issues TWO inserts -- the shared
        // columns into `assessment`, then the subtype columns into `objective_assessment`
        // with the same id. Watch the SQL log to see it.
        return assessmentRepository.save(assessment);
    }

    @Override
    @Transactional
    public PerformanceAssessment createPerformance(Long competencyId, String title, int minScore,
                                                   int maxScore, String rubricUrl, Integer wordLimit) {
        Competency competency = loadCompetency(competencyId);
        PerformanceAssessment assessment = new PerformanceAssessment(
                competency, title, new ScoreRange(minScore, maxScore), rubricUrl, wordLimit);
        return assessmentRepository.save(assessment);
    }

    private Competency loadCompetency(Long competencyId) {
        return competencyRepository.findById(competencyId)
                .orElseThrow(() -> new NotFoundException("Competency not found: " + competencyId));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Assessment assessment = findById(id);

        // Same reasoning as CompetencyServiceImpl.delete: submissions reference assessments
        // with no cascade, so graded work blocks the delete. 409 with a reason, not a 500
        // from a foreign-key violation.
        if (submissionRepository.existsByAssessmentId(id)) {
            throw new BusinessRuleException(
                    "Assessment " + id + " has student submissions and cannot be deleted");
        }

        // One call, two DELETEs (subtype row, then parent row). Hibernate knows the order
        // from the JOINED mapping.
        assessmentRepository.delete(assessment);
    }
}
