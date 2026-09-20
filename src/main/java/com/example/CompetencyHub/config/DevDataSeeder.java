package com.example.CompetencyHub.config;

import com.example.CompetencyHub.domain.embeddable.Address;
import com.example.CompetencyHub.domain.model.Competency;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.domain.model.Student;
import com.example.CompetencyHub.repository.CourseRepository;
import com.example.CompetencyHub.repository.StudentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Populates the database with sample courses on startup.
 *
 * <p>{@code @Profile("dev")} means this bean only exists when the dev profile is
 * active — the class is never instantiated in any other environment, so there is no
 * risk of seed data reaching production. That is the profile mechanism doing what
 * an {@code if (isDev())} check would otherwise do, without the check.
 *
 * <p>{@code CommandLineRunner} runs once after the application context is ready.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;

    public DevDataSeeder(CourseRepository courseRepository, StudentRepository studentRepository) {
        this.courseRepository = courseRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    public void run(String... args) {
        // Idempotent: re-running the app must not duplicate the seed data.
        // Students to enroll. Five is enough to fill a small course in testing.
        if (studentRepository.count() == 0) {
            for (int i = 1; i <= 5; i++) {
                studentRepository.save(new Student(
                        "Student", "Number" + i, "student%d@example.com".formatted(i),
                        new Address("%d Main St".formatted(i), "Dallas", "TX", "75080")
                ));
            }
        }

        // One deliberately tiny course, so the seat limit is easy to hit by hand.
        if (!courseRepository.existsByCode("CS999")) {
            Course tiny = new Course("CS999", "One Seat Only", "For testing seat limits", 1);
            courseRepository.save(tiny);
        }

        if (courseRepository.count() == 0) {
            for (int i = 1; i <= 10; i++) {
                Course course = new Course(
                        "CS%03d".formatted(500 + i),
                        "Sample Course " + i,
                        "Seeded course for local development",
                        30
                );

                // Four competencies each. addCompetency() sets both sides of the
                // relationship, so the foreign key is populated on save.
                for (int j = 1; j <= 4; j++) {
                    course.addCompetency(new Competency("Competency " + j, 25, j));
                }

                // Cascade = ALL on Course.competencies means saving the course saves
                // its competencies too — one call, five inserts.
                courseRepository.save(course);
            }
        }
    }
}