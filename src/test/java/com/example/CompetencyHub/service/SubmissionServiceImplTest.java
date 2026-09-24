package com.example.CompetencyHub.service;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.common.exception.InvalidInputException;
import com.example.CompetencyHub.common.exception.NotFoundException;
import com.example.CompetencyHub.domain.embeddable.ScoreRange;
import com.example.CompetencyHub.domain.enums.SubmissionStatus;
import com.example.CompetencyHub.domain.model.*;
import com.example.CompetencyHub.messaging.event.SubmissionGradedEvent;
import com.example.CompetencyHub.repository.*;
import com.example.CompetencyHub.service.impl.SubmissionServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static com.example.CompetencyHub.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceImplTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private AssessmentRepository assessmentRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private MentorRepository mentorRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private SubmissionServiceImpl service;

    private Student student;
    private Course course;
    private ObjectiveAssessment quiz;          // 0-100, pass 70
    private PerformanceAssessment essay;       // 0-100, pass 60, max 5 words

    @BeforeEach
    void setUp() {
        service = new SubmissionServiceImpl(submissionRepository, assessmentRepository,
                studentRepository, mentorRepository, enrollmentRepository, eventPublisher,
                new SimpleMeterRegistry());

        student = student();
        ReflectionTestUtils.setField(student, "id", 1L);
        course = course();
        ReflectionTestUtils.setField(course, "id", 10L);
        Competency competency = new Competency("Transactions", 25, 1);
        course.addCompetency(competency);

        quiz = objectiveAssessment(competency, "Quiz");
        ReflectionTestUtils.setField(quiz, "id", 100L);
        essay = new PerformanceAssessment(competency, "Essay", new ScoreRange(0, 100), 60, null, 5);
        ReflectionTestUtils.setField(essay, "id", 200L);
    }

    // ---- submit -------------------------------------------------------------

    @Test
    void anObjectiveTestIsGradedOnSubmissionAndPublishesAnEvent() {
        givenEnrolled(quiz);
        when(submissionRepository.countByStudentIdAndAssessmentId(1L, 100L)).thenReturn(0L);
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission result = service.submit(100L, 1L, 85, null);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(result.getScore()).isEqualTo(85);
        assertThat(result.getGradedBy()).isNull();   // no human involved
        assertThat(result.getAttemptNumber()).isEqualTo(1);

        ArgumentCaptor<SubmissionGradedEvent> event = ArgumentCaptor.forClass(SubmissionGradedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().autoGraded()).isTrue();
        assertThat(event.getValue().passed()).isTrue();
        assertThat(event.getValue().courseId()).isEqualTo(10L);
    }

    @Test
    void aPerformanceTaskIsQueuedAndPublishesNothing() {
        givenEnrolled(essay);
        when(submissionRepository.countByStudentIdAndAssessmentId(1L, 200L)).thenReturn(2L);
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission result = service.submit(200L, 1L, null, "my short answer");

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(result.getAttemptNumber()).isEqualTo(3);   // two before it
        // Nothing to tell anyone yet -- the grade does not exist.
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void aStudentNotEnrolledInTheCourseCannotSubmit() {
        when(assessmentRepository.findById(100L)).thenReturn(Optional.of(quiz));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(enrollmentRepository.findFirstByStudentIdAndCourseIdAndStatusIn(eq(1L), eq(10L), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(100L, 1L, 85, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not actively enrolled");
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void anObjectiveTestWithoutAScoreIs400() {
        givenEnrolled(quiz);

        assertThatThrownBy(() -> service.submit(100L, 1L, null, null))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void aPerformanceTaskSentWithAScoreIs400() {
        givenEnrolled(essay);

        // A student grading their own essay. The API must refuse, not quietly drop the score.
        assertThatThrownBy(() -> service.submit(200L, 1L, 100, "my answer"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("mentor");
    }

    @Test
    void aPerformanceTaskOverTheWordLimitIs400() {
        givenEnrolled(essay);

        assertThatThrownBy(() -> service.submit(200L, 1L, null, "one two three four five six"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("word limit");
    }

    @Test
    void anObjectiveScoreOutsideTheRangeIs400() {
        givenEnrolled(quiz);

        assertThatThrownBy(() -> service.submit(100L, 1L, 150, null))
                .isInstanceOf(InvalidInputException.class);
        verify(submissionRepository, never()).save(any());
    }

    // ---- grade --------------------------------------------------------------

    @Test
    void mentorGradingPublishesANonAutoEvent() {
        Submission queued = new Submission(student, essay, 1, "answer");
        ReflectionTestUtils.setField(queued, "id", 5L);
        Mentor mentor = mentor();
        when(submissionRepository.findById(5L)).thenReturn(Optional.of(queued));
        when(mentorRepository.findById(3L)).thenReturn(Optional.of(mentor));

        Submission result = service.grade(5L, 3L, 55, "Needs more detail");

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(result.getGradedBy()).isSameAs(mentor);

        ArgumentCaptor<SubmissionGradedEvent> event = ArgumentCaptor.forClass(SubmissionGradedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().autoGraded()).isFalse();
        assertThat(event.getValue().passed()).isFalse();   // 55 < 60
        assertThat(event.getValue().submissionId()).isEqualTo(5L);
    }

    @Test
    void gradingAnAlreadyGradedSubmissionIs409AndPublishesNothing() {
        Submission graded = gradedSubmission(student, quiz, 80);
        when(submissionRepository.findById(5L)).thenReturn(Optional.of(graded));
        when(mentorRepository.findById(3L)).thenReturn(Optional.of(mentor()));

        assertThatThrownBy(() -> service.grade(5L, 3L, 90, null))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void gradingWithAnUnknownMentorIs404() {
        when(submissionRepository.findById(5L))
                .thenReturn(Optional.of(new Submission(student, essay, 1, "answer")));
        when(mentorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grade(5L, 99L, 70, null))
                .isInstanceOf(NotFoundException.class);
    }

    private void givenEnrolled(Assessment assessment) {
        when(assessmentRepository.findById(assessment.getId())).thenReturn(Optional.of(assessment));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(enrollmentRepository.findFirstByStudentIdAndCourseIdAndStatusIn(eq(1L), eq(10L), any()))
                .thenReturn(Optional.of(new Enrollment(student, course)));
    }
}
