package com.example.CompetencyHub.service.impl;

import com.example.CompetencyHub.common.exception.AlreadyEnrolledException;
import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.common.exception.NotFoundException;
import com.example.CompetencyHub.domain.enums.AttemptOutcome;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.domain.model.Enrollment;
import com.example.CompetencyHub.domain.model.Student;
import com.example.CompetencyHub.messaging.event.EnrollmentCreatedEvent;
import com.example.CompetencyHub.repository.CourseRepository;
import com.example.CompetencyHub.repository.EnrollmentRepository;
import com.example.CompetencyHub.repository.StudentRepository;
import com.example.CompetencyHub.service.EnrollmentAuditService;
import com.example.CompetencyHub.service.EnrollmentService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EnrollmentServiceImpl implements EnrollmentService {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public EnrollmentServiceImpl(StudentRepository studentRepository,
                                 CourseRepository courseRepository,
                                 EnrollmentRepository enrollmentRepository,
                                 EnrollmentAuditService auditService,
                                 ApplicationEventPublisher eventPublisher) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Enrolls a student, claiming one seat.
     *
     * <p><b>The transaction boundary is this method.</b> Two things must happen together
     * or not at all: the seat count drops by one, and an enrollment row appears. Half of
     * that is worse than neither — a decremented seat with no enrollment silently shrinks
     * the course forever.
     *
     * <p>Default propagation REQUIRED: this is the outermost transaction in the request,
     * so one is started here and everything below joins it.
     *
     * <p>Every exception thrown here is unchecked, so Spring rolls back automatically.
     */
    @Override
    @Transactional
    public Enrollment enroll(Long studentId, Long courseId) {
        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));

            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));

            // Checked before reserving a seat, so a duplicate request cannot consume one.
            if (enrollmentRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
                throw new AlreadyEnrolledException(
                        "Student " + studentId + " is already enrolled in course " + courseId);
            }

            // Throws CourseFullException when nothing is left. The entity enforces it.
            course.reserveSeat();

            // No courseRepository.save(course) call, and none is needed: `course` is a
            // managed entity inside this transaction, so Hibernate's dirty checking
            // detects the changed field and issues the UPDATE at commit. That UPDATE
            // carries the optimistic-lock check (AND version = ?).
            Enrollment enrollment = enrollmentRepository.save(new Enrollment(student, course));

            // In-process event. Spring holds it until the transaction commits, then hands it
            // to the @TransactionalEventListener above. Nothing reaches Kafka until then.
            eventPublisher.publishEvent(new EnrollmentCreatedEvent(
                    enrollment.getId(),
                    student.getId(),
                    student.getEmail(),
                    course.getId(),
                    course.getCode(),
                    course.getTitle(),
                    Instant.now()
            ));

            // Commits in its own transaction. Harmless on the happy path.
            auditService.record(studentId, courseId, AttemptOutcome.SUCCESS, null);

            return enrollment;

        } catch (BusinessRuleException e) {
            // Record the refusal, then rethrow so the outer transaction still rolls back.
            // The audit insert survives because it ran in its own transaction; without
            // REQUIRES_NEW this line would write a row that is immediately discarded.
            auditService.record(studentId, courseId, AttemptOutcome.REJECTED, e.getMessage());
            throw e;
        }
    }

    /**
     * Withdraws an enrollment and returns the seat to the pool.
     *
     * <p>Marks the enrollment WITHDRAWN rather than deleting it — a student who took a
     * course and left is a different historical fact from one who never enrolled, and
     * deleting the row would erase that.
     */
    @Override
    @Transactional
    public void withdraw(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException("Enrollment not found: " + enrollmentId));

        enrollment.withdraw();
        enrollment.getCourse().releaseSeat();
        // Both entities are managed; dirty checking flushes both updates at commit.
    }
}
