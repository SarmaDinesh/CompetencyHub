package com.example.CompetencyHub.service.impl;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.common.exception.InvalidInputException;
import com.example.CompetencyHub.common.exception.NotFoundException;
import com.example.CompetencyHub.domain.enums.EnrollmentStatus;
import com.example.CompetencyHub.domain.enums.SubmissionStatus;
import com.example.CompetencyHub.domain.model.*;
import com.example.CompetencyHub.messaging.event.SubmissionGradedEvent;
import com.example.CompetencyHub.repository.*;
import com.example.CompetencyHub.service.SubmissionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

@Service
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AssessmentRepository assessmentRepository;
    private final StudentRepository studentRepository;
    private final MentorRepository mentorRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final Counter autoGraded;
    private final Counter mentorGraded;

    public SubmissionServiceImpl(SubmissionRepository submissionRepository,
                                 AssessmentRepository assessmentRepository,
                                 StudentRepository studentRepository,
                                 MentorRepository mentorRepository,
                                 EnrollmentRepository enrollmentRepository,
                                 ApplicationEventPublisher eventPublisher,
                                 MeterRegistry meterRegistry) {
        this.submissionRepository = submissionRepository;
        this.assessmentRepository = assessmentRepository;
        this.studentRepository = studentRepository;
        this.mentorRepository = mentorRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.eventPublisher = eventPublisher;

        // One metric, a bounded tag -- same shape as enrollment.attempts.
        this.autoGraded = Counter.builder("submissions.graded").tag("mode", "auto").register(meterRegistry);
        this.mentorGraded = Counter.builder("submissions.graded").tag("mode", "mentor").register(meterRegistry);
    }

    @Override
    @Transactional
    public Submission submit(Long assessmentId, Long studentId, Integer score, String content) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new NotFoundException("Assessment not found: " + assessmentId));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));

        // Only students taking the course may submit to it. Walks assessment -> competency ->
        // course inside the transaction, so the lazy associations can load.
        Long courseId = assessment.getCompetency().getCourse().getId();
        boolean enrolled = enrollmentRepository
                .findFirstByStudentIdAndCourseIdAndStatusIn(
                        studentId, courseId, EnumSet.of(EnrollmentStatus.ACTIVE))
                .isPresent();
        if (!enrolled) {
            throw new BusinessRuleException(
                    "Student " + studentId + " is not actively enrolled in course " + courseId);
        }

        // Count-then-insert can race: two simultaneous submissions both read "1 so far" and
        // both claim attempt 2. The UNIQUE (student, assessment, attempt_number) constraint in
        // V9 stops the second one; the handler turns that into a 409. Rare enough that a retry
        // is the right answer -- not worth a lock on every submission.
        int attempt = (int) submissionRepository.countByStudentIdAndAssessmentId(studentId, assessmentId) + 1;

        /*
         * The rules differ by assessment type, so the type decides -- pattern matching again,
         * now on the ENTITY. Assessment is not sealed (Hibernate proxies subclass entities), so
         * a default is required. findById() in a fresh transaction returns the real subtype,
         * not a proxy, so the default is a guard, not a path we expect.
         */
        Submission submission = switch (assessment) {
            case ObjectiveAssessment objective -> {
                if (score == null) {
                    throw new InvalidInputException("score is required for an objective assessment");
                }
                Submission s = new Submission(student, objective, attempt, null);
                // Auto-graded: the test marks itself. No mentor, no queue. grade() still runs
                // the range check and the state transition -- one path to GRADED, not two.
                s.grade(score, null, null);
                autoGraded.increment();
                yield s;
            }
            case PerformanceAssessment performance -> {
                if (score != null) {
                    throw new InvalidInputException(
                            "A performance task is scored by a mentor; do not send a score");
                }
                if (content == null || content.isBlank()) {
                    throw new InvalidInputException("content is required for a performance task");
                }
                if (!performance.fitsWordLimit(content)) {
                    throw new InvalidInputException(
                            "content exceeds the word limit of " + performance.getWordLimit());
                }
                yield new Submission(student, performance, attempt, content);
            }
            default -> throw new IllegalStateException(
                    "Unhandled assessment type: " + assessment.getClass().getName());
        };

        submissionRepository.save(submission);

        if (submission.isGraded()) {
            publishGraded(submission, true);
        }
        return submission;
    }

    @Override
    @Transactional(readOnly = true)
    public Submission findById(Long id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Submission not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Submission> findByStudent(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new NotFoundException("Student not found: " + studentId);
        }
        return submissionRepository.findByStudentIdOrderBySubmittedAtDesc(studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Submission> findByAssessment(Long assessmentId, SubmissionStatus status) {
        if (!assessmentRepository.existsById(assessmentId)) {
            throw new NotFoundException("Assessment not found: " + assessmentId);
        }
        return status == null
                ? submissionRepository.findByAssessmentIdOrderBySubmittedAtAsc(assessmentId)
                : submissionRepository.findByAssessmentIdAndStatusOrderBySubmittedAtAsc(assessmentId, status);
    }

    @Override
    @Transactional
    public Submission grade(Long submissionId, Long mentorId, int score, String feedback) {
        Submission submission = findById(submissionId);
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new NotFoundException("Mentor not found: " + mentorId));

        // All the rules live in the entity: already graded -> 409, score out of range -> 400.
        // A second mentor racing this one passes the status check on their own copy and is
        // stopped by @Version at commit instead -- also a 409, via the optimistic-lock handler.
        submission.grade(score, feedback, mentor);
        mentorGraded.increment();

        publishGraded(submission, false);
        return submission;
    }

    /**
     * In-process event, exactly like enrollment: Spring holds it until this transaction
     * commits, then SubmissionEventPublisher sends it to Kafka. If the grade rolls back, no
     * message was ever sent -- nobody gets told about a grade that does not exist.
     *
     * <p>Built here, inside the transaction, because it reads through lazy associations
     * (student email, course code). After commit the session is closed and those reads would
     * throw LazyInitializationException.
     */
    private void publishGraded(Submission submission, boolean auto) {
        Assessment assessment = submission.getAssessment();
        Course course = assessment.getCompetency().getCourse();
        Student student = submission.getStudent();

        eventPublisher.publishEvent(new SubmissionGradedEvent(
                submission.getId(),
                student.getId(),
                student.getEmail(),
                assessment.getId(),
                assessment.getTitle(),
                course.getId(),
                course.getCode(),
                submission.getScore(),
                submission.isPassing(),
                auto,
                Instant.now()
        ));
    }
}
