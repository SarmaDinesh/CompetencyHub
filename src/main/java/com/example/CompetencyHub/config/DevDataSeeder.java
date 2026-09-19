package com.example.CompetencyHub.config;

import com.example.CompetencyHub.domain.model.Competency;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.repository.CourseRepository;
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

    public DevDataSeeder(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public void run(String... args) {
        // Idempotent: re-running the app must not duplicate the seed data.
        if (courseRepository.count() > 0) {
            return;
        }

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