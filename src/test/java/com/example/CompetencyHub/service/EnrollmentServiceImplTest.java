package com.example.CompetencyHub.service;

import com.example.CompetencyHub.common.exception.AlreadyEnrolledException;
import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.domain.enums.AttemptOutcome;
import com.example.CompetencyHub.domain.enums.EnrollmentStatus;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.domain.model.Enrollment;
import com.example.CompetencyHub.domain.model.Student;
import com.example.CompetencyHub.repository.CourseRepository;
import com.example.CompetencyHub.repository.EnrollmentRepository;
import com.example.CompetencyHub.repository.StudentRepository;
import com.example.CompetencyHub.service.impl.EnrollmentServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static com.example.CompetencyHub.TestFixtures.course;
import static com.example.CompetencyHub.TestFixtures.student;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Plain Mockito, no Spring context: runs in milliseconds under `mvn test`.
 *
 * The service is built by hand in @BeforeEach rather than with @InjectMocks, because its
 * constructor needs a real MeterRegistry (it registers counters). SimpleMeterRegistry is
 * Micrometer's in-memory registry, made for exactly this.
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceImplTest {

    @Mock private StudentRepository studentRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private EnrollmentAuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private EnrollmentServiceImpl service;

    private Student student;
    private Course course;

    @BeforeEach
    void setUp() {
        service = new EnrollmentServiceImpl(studentRepository, courseRepository,
                enrollmentRepository, auditService, eventPublisher, new SimpleMeterRegistry());

        student = student();
        ReflectionTestUtils.setField(student, "id", 1L);
        course = course("CS544", "Enterprise Architecture", 30);
        ReflectionTestUtils.setField(course, "id", 10L);
    }

    // ---- Bug 1: double withdraw -------------------------------------------

    @Test
    void withdrawingReturnsTheSeat() {
        Enrollment enrollment = activeEnrollment(100L);   // takes a seat: 29 left
        when(enrollmentRepository.findById(100L)).thenReturn(Optional.of(enrollment));

        service.withdraw(100L);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(course.getSeatsAvailable()).isEqualTo(30);
    }

    @Test
    void withdrawingTwiceIsRejectedAndReleasesOnlyOneSeat() {
        // Two students enrolled, so the capacity guard in releaseSeat() cannot hide the bug.
        // With one student, a second release would be capped at capacity and the test would
        // pass even on the old code.
        activeEnrollment(99L);
        Enrollment enrollment = activeEnrollment(100L);   // 28 left
        when(enrollmentRepository.findById(100L)).thenReturn(Optional.of(enrollment));

        service.withdraw(100L);                            // 29 left

        assertThatThrownBy(() -> service.withdraw(100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("WITHDRAWN");

        assertThat(course.getSeatsAvailable()).isEqualTo(29);   // not 30
    }

    @Test
    void aCompletedEnrollmentCannotBeWithdrawn() {
        Enrollment enrollment = enrollmentWithStatus(100L, EnrollmentStatus.COMPLETED);
        when(enrollmentRepository.findById(100L)).thenReturn(Optional.of(enrollment));
        int seatsBefore = course.getSeatsAvailable();

        assertThatThrownBy(() -> service.withdraw(100L))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(course.getSeatsAvailable()).isEqualTo(seatsBefore);
    }

    // ---- Bug 2: re-enrollment ---------------------------------------------

    @Test
    void aStudentWhoWithdrewCanEnrollAgain() {
        givenStudentAndCourseExist();
        // Only a WITHDRAWN row exists, and WITHDRAWN is not in the blocking set, so the
        // repository finds nothing blocking.
        when(enrollmentRepository.findFirstByStudentIdAndCourseIdAndStatusIn(eq(1L), eq(10L), any()))
                .thenReturn(Optional.empty());
        when(enrollmentRepository.save(any(Enrollment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Enrollment result = service.enroll(1L, 10L);

        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(course.getSeatsAvailable()).isEqualTo(29);
        verify(auditService).record(1L, 10L, AttemptOutcome.SUCCESS, null);
    }

    @Test
    void anActiveEnrollmentBlocksASecondOne() {
        givenStudentAndCourseExist();
        Enrollment existing = enrollmentWithStatus(100L, EnrollmentStatus.ACTIVE);
        when(enrollmentRepository.findFirstByStudentIdAndCourseIdAndStatusIn(eq(1L), eq(10L), any()))
                .thenReturn(Optional.of(existing));
        int seatsBefore = course.getSeatsAvailable();

        assertThatThrownBy(() -> service.enroll(1L, 10L))
                .isInstanceOf(AlreadyEnrolledException.class)
                .hasMessageContaining("already enrolled");

        // The refusal happens before reserveSeat(), so no seat is consumed.
        assertThat(course.getSeatsAvailable()).isEqualTo(seatsBefore);
        verify(enrollmentRepository, never()).save(any());
        verify(auditService).record(eq(1L), eq(10L), eq(AttemptOutcome.REJECTED), anyString());
    }

    @Test
    void aCompletedCourseCannotBeRetaken() {
        givenStudentAndCourseExist();
        Enrollment existing = enrollmentWithStatus(100L, EnrollmentStatus.COMPLETED);
        when(enrollmentRepository.findFirstByStudentIdAndCourseIdAndStatusIn(eq(1L), eq(10L), any()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.enroll(1L, 10L))
                .isInstanceOf(AlreadyEnrolledException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    void theBlockingCheckAsksForActiveAndCompletedButNotWithdrawn() {
        givenStudentAndCourseExist();
        when(enrollmentRepository.findFirstByStudentIdAndCourseIdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(Optional.empty());
        when(enrollmentRepository.save(any(Enrollment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.enroll(1L, 10L);

        // Pins the rule itself. If someone adds WITHDRAWN to the set, re-enrollment silently
        // breaks again and every test above still passes -- because they mock the answer.
        verify(enrollmentRepository).findFirstByStudentIdAndCourseIdAndStatusIn(
                eq(1L), eq(10L),
                argThat(statuses -> statuses.contains(EnrollmentStatus.ACTIVE)
                        && statuses.contains(EnrollmentStatus.COMPLETED)
                        && !statuses.contains(EnrollmentStatus.WITHDRAWN)));
    }

    // ---- helpers ------------------------------------------------------------

    private void givenStudentAndCourseExist() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
    }

    /** An ACTIVE enrollment that has claimed a seat, as enroll() would leave it. */
    private Enrollment activeEnrollment(Long id) {
        course.reserveSeat();
        Enrollment enrollment = new Enrollment(student, course);
        ReflectionTestUtils.setField(enrollment, "id", id);
        return enrollment;
    }

    private Enrollment enrollmentWithStatus(Long id, EnrollmentStatus status) {
        Enrollment enrollment = activeEnrollment(id);
        // No public way to reach COMPLETED yet -- nothing in the app completes a course.
        // Reflection is the test seam until that feature exists.
        ReflectionTestUtils.setField(enrollment, "status", status);
        return enrollment;
    }
}
