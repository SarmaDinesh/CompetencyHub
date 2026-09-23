package com.example.CompetencyHub.service;

import com.example.CompetencyHub.TestcontainersConfig;
import com.example.CompetencyHub.common.exception.CourseFullException;
import com.example.CompetencyHub.domain.model.Course;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.*;

import static com.example.CompetencyHub.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class ConcurrentEnrollmentIT {

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private EnrollmentEventPublisher enrollmentEventPublisher;

    /**
     * Manual cleanup, which is the price of dropping @Transactional.
     *
     * Everywhere else the test transaction rolls back and the database is untouched. Here
     * the writes are real and committed, so they survive the test -- and both Course.code
     * and Student.email are UNIQUE, so a second run would fail on a duplicate key rather
     * than on anything to do with concurrency. Worse, the leftover rows are visible to
     * every other test sharing this Spring context.
     *
     * Deleting in @AfterEach rather than @BeforeEach so a failure leaves the database
     * clean for the next test instead of blaming it.
     */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    progress, enrollment, enrollment_attempt, notification,
                    submission, objective_assessment, performance_assessment,
                    assessment, competency, course, student, job_run
                RESTART IDENTITY CASCADE
                """);
    }

    /**
     * Deliberately NOT @Transactional, and understanding why is the point of the test.
     *
     * A @Transactional test runs everything in ONE transaction on ONE connection. The two
     * "concurrent" enrollments would share that transaction, see each other's uncommitted
     * writes, and never conflict. The test would pass green while proving nothing at all --
     * the worst possible outcome, because you would then trust it.
     *
     * Real concurrency needs real separate transactions on separate connections, which
     * means no test-managed transaction and no automatic rollback.
     */
    @Test
    void twoStudentsRacingForTheLastSeatProduceExactlyOneWinner() throws Exception {
        Course course = courseRepository.save(singleSeatCourse("RACE-1"));
        Student ada = studentRepository.save(student("Ada", "Lovelace", "ada-race@example.com"));
        Student alan = studentRepository.save(student("Alan", "Turing", "alan-race@example.com"));

        // Both threads park on this latch, then are released together. Without it, thread
        // one finishes before thread two starts and there is no race to observe -- the
        // test would pass on a machine where it should fail.
        CountDownLatch startSignal = new CountDownLatch(1);

        // try-with-resources: ExecutorService is AutoCloseable since Java 19, and close()
        // shuts down and awaits termination. Replaces the shutdown() call that a failing
        // assertion would have skipped, leaking threads into the rest of the suite.
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {

            Future<Boolean> first = pool.submit(attempt(startSignal, ada.getId(), course.getId()));
            Future<Boolean> second = pool.submit(attempt(startSignal, alan.getId(), course.getId()));

            startSignal.countDown();

            List<Boolean> outcomes = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            // Exactly one winner. Two means @Version is not protecting seats_available and
            // the course is oversold, which is the entire reason optimistic locking exists.
            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        }

        assertThat(courseRepository.findById(course.getId()).orElseThrow().getSeatsAvailable())
                .isZero();

        // The seat count and the enrollment count must agree. Checking only seatsAvailable
        // would pass if the losing transaction decremented the seat but failed to insert --
        // a course that looks full with one student in it.
        assertThat(enrollmentRepository.count()).isEqualTo(1);
    }

    private Callable<Boolean> attempt(CountDownLatch start, Long studentId, Long courseId) {
        return () -> {
            start.await();
            try {
                enrollmentService.enroll(studentId, courseId);
                return true;
            } catch (ObjectOptimisticLockingFailureException | CourseFullException ex) {
                /*
                 * Both are legitimate losses, and which one fires depends on timing:
                 *
                 *   - Both threads read seatsAvailable = 1, both decrement, one commits.
                 *     The second hits the @Version check and gets
                 *     ObjectOptimisticLockingFailureException.
                 *   - One commits before the other reads. The second sees
                 *     seatsAvailable = 0 and Course.reserveSeat() throws CourseFullException.
                 *
                 * Accepting either is correct. Accepting only one would make the test flaky
                 * on a faster or slower machine, which is worse than no test.
                 */
                return false;
            }
        };
    }
}
