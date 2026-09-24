package com.example.CompetencyHub.service;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.common.exception.NotFoundException;
import com.example.CompetencyHub.domain.model.Assessment;
import com.example.CompetencyHub.domain.model.Competency;
import com.example.CompetencyHub.domain.model.ObjectiveAssessment;
import com.example.CompetencyHub.domain.model.PerformanceAssessment;
import com.example.CompetencyHub.repository.AssessmentRepository;
import com.example.CompetencyHub.repository.CompetencyRepository;
import com.example.CompetencyHub.repository.SubmissionRepository;
import com.example.CompetencyHub.service.impl.AssessmentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.example.CompetencyHub.TestFixtures.objectiveAssessment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceImplTest {

    @Mock private AssessmentRepository assessmentRepository;
    @Mock private CompetencyRepository competencyRepository;
    @Mock private SubmissionRepository submissionRepository;

    @InjectMocks private AssessmentServiceImpl service;

    private final Competency competency = new Competency("Transactions", 25, 1);

    @Test
    void createsAnObjectiveAssessment() {
        when(competencyRepository.findById(7L)).thenReturn(Optional.of(competency));
        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(inv -> inv.getArgument(0));

        ObjectiveAssessment created = service.createObjective(7L, "Quiz 1", 0, 100, 20, 70);

        assertThat(created.getCompetency()).isSameAs(competency);
        assertThat(created.getScoreRange().getMaxScore()).isEqualTo(100);
        assertThat(created.getPassingScore()).isEqualTo(70);
        assertThat(created.describeFormat()).isEqualTo("20-question test, pass at 70");
    }

    @Test
    void createsAPerformanceAssessmentWithNoWordLimit() {
        when(competencyRepository.findById(7L)).thenReturn(Optional.of(competency));
        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(inv -> inv.getArgument(0));

        PerformanceAssessment created = service.createPerformance(7L, "Essay", 0, 100, 60, null, null);

        // Integer, not int: null survives, meaning "no limit" -- not a limit of zero words.
        assertThat(created.getWordLimit()).isNull();
        assertThat(created.describeFormat()).isEqualTo("Performance task, graded by rubric, pass at 60");
        // Pulled up in V9: the pass mark now lives on the parent, for every subtype.
        assertThat(created.getPassingScore()).isEqualTo(60);
    }

    @Test
    void creatingUnderAnUnknownCompetencyIs404() {
        when(competencyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createObjective(99L, "Quiz", 0, 100, 10, 70))
                .isInstanceOf(NotFoundException.class);
        verify(assessmentRepository, never()).save(any());
    }

    @Test
    void anAssessmentWithSubmissionsCannotBeDeleted() {
        Assessment quiz = objectiveAssessment(competency, "Quiz");
        when(assessmentRepository.findById(3L)).thenReturn(Optional.of(quiz));
        when(submissionRepository.existsByAssessmentId(3L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(3L))
                .isInstanceOf(BusinessRuleException.class);
        verify(assessmentRepository, never()).delete(any());
    }

    @Test
    void anAssessmentWithoutSubmissionsIsDeleted() {
        Assessment quiz = objectiveAssessment(competency, "Quiz");
        when(assessmentRepository.findById(3L)).thenReturn(Optional.of(quiz));
        when(submissionRepository.existsByAssessmentId(3L)).thenReturn(false);

        service.delete(3L);

        verify(assessmentRepository).delete(quiz);
    }
}
