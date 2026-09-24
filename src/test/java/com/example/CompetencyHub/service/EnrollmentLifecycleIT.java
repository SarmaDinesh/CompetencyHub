package com.example.CompetencyHub.service;

import com.example.CompetencyHub.TestcontainersConfig;
import com.example.CompetencyHub.common.exception.AlreadyEnrolledException;
import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.domain.enums.EnrollmentStatus;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.domain.model.Enrollment;
import com.example.CompetencyHub.domain.model.Student;
import com.example.CompetencyHub.messaging.EnrollmentEventPublisher;
import com.example.CompetencyHub.repository.CourseRepository;
import com.example.CompetencyHub.repository.EnrollmentRepository;
import com.example.CompetencyHub.repository.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.example.CompetencyHub.TestFixtures.course;
import static com.example.CompetencyHub.TestFixtures.student;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The enrollment rules against real Postgres, because two of them live in the database:
 * the V8 partial unique index, and the @Version check on course.
 *
 * Not @Transactional, same reason as ConcurrentEnrollmentIT: each service call must commit
 * on its own, the way it does in production. A test-wide transaction would make the second
 * withdraw see the first one's uncommitted change through the same persistence context,
 * which is not what a second HTTP request sees.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class EnrollmentLifecycleIT {

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    // Keeps Kafka out of it: the after-commit publisher is replaced with a no-op mock.
    @MockitoBean private EnrollmentEventPublisher enrollmentEventPublisher;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    progress, enrollment, enrollment_attempt, notification,
                    submission, objective_assessment, performance_assessment,
                    assessment, competency, course, student, mentor, job_run
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void withdrawThenReEnrollKeepsHistoryAndSeatsAddUp() {
        Course course = courseRepository.save(course("CS544", "Enterprise Architecture", 5));
        Student ada = studentRepository.save(student("Ada", "Lovelace", "ada@example.com"));

        Enrollment first = enrollmentService.enroll(ada.getId(), course.getId());
        enrollmentService.withdraw(first.getId());
        Enrollment second = enrollmentService.enroll(ada.getId(), course.getId());

        // A NEW row, not the old one flipped back. The withdrawal stays on record.
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(enrollmentRepository.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(enrollmentRepository.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(EnrollmentStatus.ACTIVE);

        // enroll (-1), withdraw (+1), enroll (-1): one seat taken.
        assertThat(seatsLeft(course)).isEqualTo(4);
    }

    @Test
    void secondWithdrawIsRejectedAndTheSeatCountStaysHonest() {
        Course course = courseRepository.save(course("CS544", "Enterprise Architecture", 5));
        Student ada = studentRepository.save(student("Ada", "Lovelace", "ada@example.com"));
        Student alan = studentRepository.save(student("Alan", "Turing", "alan@example.com"));

        enrollmentService.enroll(alan.getId(), course.getId());          // 4 left
        Enrollment adas = enrollmentService.enroll(ada.getId(), course.getId());   // 3 left
        enrollmentService.withdraw(adas.getId());                         // 4 left

        assertThatThrownBy(() -> enrollmentService.withdraw(adas.getId()))
                .isInstanceOf(BusinessRuleException.class);

        // The old code would say 5 here: a course with one student and every seat free.
        assertThat(seatsLeft(course)).isEqualTo(4);
    }

    @Test
    void enrollingTwiceWhileActiveIsStillRejected() {
        Course course = courseRepository.save(course("CS544", "Enterprise Architecture", 5));
        Student ada = studentRepository.save(student("Ada", "Lovelace", "ada@example.com"));

        enrollmentService.enroll(ada.getId(), course.getId());

        assertThatThrownBy(() -> enrollmentService.enroll(ada.getId(), course.getId()))
                .isInstanceOf(AlreadyEnrolledException.class);
        assertThat(seatsLeft(course)).isEqualTo(4);
    }

    /**
     * Goes around the service on purpose, straight to SQL, to prove the DATABASE refuses two
     * ACTIVE rows for one pair. The service check can be raced; this index cannot.
     */
    @Test
    void theDatabaseItselfRefusesTwoActiveRowsForTheSamePair() {
        Course course = courseRepository.save(course("CS544", "Enterprise Architecture", 5));
        Student ada = studentRepository.save(student("Ada", "Lovelace", "ada@example.com"));

        String insert = """
                INSERT INTO enrollment (student_id, course_id, enrolled_at, status)
                VALUES (?, ?, now(), ?)
                """;

        // Any number of WITHDRAWN rows is fine...
        jdbcTemplate.update(insert, ada.getId(), course.getId(), "WITHDRAWN");
        jdbcTemplate.update(insert, ada.getId(), course.getId(), "WITHDRAWN");
        // ...and one ACTIVE alongside them is fine...
        jdbcTemplate.update(insert, ada.getId(), course.getId(), "ACTIVE");

        // ...but a second ACTIVE is not.
        assertThatThrownBy(() -> jdbcTemplate.update(insert, ada.getId(), course.getId(), "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int seatsLeft(Course course) {
        return courseRepository.findById(course.getId()).orElseThrow().getSeatsAvailable();
    }
}
