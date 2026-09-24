package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.embeddable.Address;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.domain.model.Student;
import com.example.CompetencyHub.messaging.EnrollmentEventPublisher;
import com.example.CompetencyHub.repository.CourseRepository;
import com.example.CompetencyHub.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two students, one seat, at the same instant. Exactly one must win.
 *
 * <p>Deliberately NOT annotated @Transactional. A transactional test would wrap
 * everything in one transaction that rolls back at the end — both threads would share
 * it, nothing would commit, and the race this test exists to trigger could not happen.
 * Testing concurrency requires real commits.
 *
 * <p>Runs against the local Postgres. Proper isolation with Testcontainers comes in the
 * testing module; for now, docker compose must be up.
 */
@SpringBootTest
class SeatLimitConcurrencyTest {

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StudentRepository studentRepository;

    @MockitoBean
    private EnrollmentEventPublisher enrollmentEventPublisher;

    @Test
    void twoStudentsRacingForTheLastSeat_onlyOneSucceeds() throws Exception {
        // A course with exactly one seat, and two students who both want it.
        Course course = courseRepository.save(
                new Course("RACE" + System.nanoTime(), "Race Course", "One seat", 1));

        Student first = studentRepository.save(new Student(
                "First", "Racer", "first%d@example.com".formatted(System.nanoTime()),
                new Address("1 A St", "Dallas", "TX", "75080")));
        Student second = studentRepository.save(new Student(
                "Second", "Racer", "second%d@example.com".formatted(System.nanoTime()),
                new Address("2 B St", "Dallas", "TX", "75080")));

        // A latch so both threads start at the same moment. Without it one would
        // finish before the other begins and there would be no race to observe.
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        Callable<Void> attempt = () -> {
            startSignal.await();
            try {
                enrollmentService.enroll(
                        Thread.currentThread().getName().endsWith("1")
                                ? first.getId() : second.getId(),
                        course.getId());
                successes.incrementAndGet();
            } catch (Exception e) {
                // Either CourseFullException (the second thread read the updated count)
                // or OptimisticLockingFailureException (both read seats=1, one lost the
                // version check at commit). Which one occurs depends on timing — both
                // are correct outcomes, and both mean the seat was not oversold.
                failures.incrementAndGet();
            }
            return null;
        };

        Future<Void> a = pool.submit(attempt);
        Future<Void> b = pool.submit(attempt);

        startSignal.countDown();   // go
        a.get(10, TimeUnit.SECONDS);
        b.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);

        // The decisive assertion: the seat count never went negative.
        Course after = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(after.getSeatsAvailable()).isZero();
    }
}
