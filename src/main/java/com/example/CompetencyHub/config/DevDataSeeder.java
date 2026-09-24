package com.example.CompetencyHub.config;

import com.example.CompetencyHub.domain.embeddable.Address;
import com.example.CompetencyHub.domain.enums.Role;
import com.example.CompetencyHub.domain.model.AppUser;
import com.example.CompetencyHub.domain.model.Competency;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.domain.model.Mentor;
import com.example.CompetencyHub.domain.model.Student;
import com.example.CompetencyHub.repository.AppUserRepository;
import com.example.CompetencyHub.repository.CourseRepository;
import com.example.CompetencyHub.repository.MentorRepository;
import com.example.CompetencyHub.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final MentorRepository mentorRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * One password for every seeded login, printed in the startup log. Acceptable ONLY because
     * this class exists solely under the dev profile (@Profile("dev")) -- it is never
     * instantiated in docker or production, so these accounts never exist there.
     */
    static final String DEV_PASSWORD = "password123";

    public DevDataSeeder(CourseRepository courseRepository, StudentRepository studentRepository,
                         MentorRepository mentorRepository, AppUserRepository appUserRepository,
                         PasswordEncoder passwordEncoder) {
        this.courseRepository = courseRepository;
        this.studentRepository = studentRepository;
        this.mentorRepository = mentorRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
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

        // Was `courseRepository.count() == 0`, checked AFTER CS999 had just been saved -- so on
        // a fresh database the count was already 1 and these ten courses were never created.
        // Each block now checks for its own data, so the order of blocks cannot matter.
        if (!courseRepository.existsByCode("CS501")) {
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

        // One mentor, so grading can be tried by hand straight after startup.
        if (!mentorRepository.existsByEmail("mentor@example.com")) {
            mentorRepository.save(new Mentor("Grace", "Hopper", "mentor@example.com", "Enterprise Java"));
        }

        // ---- Logins (V10) -------------------------------------------------------------
        // The first ADMIN cannot come from the API: registration only ever creates students,
        // and only an admin can create mentors. Something has to bootstrap the first one.
        ensureLogin("admin@example.com", Role.ADMIN);

        // Give the seeded mentor and first student a login. Idempotent AND safe on a database
        // seeded before V10: the rows exist, only the link is missing, so link rather than
        // create duplicates.
        mentorRepository.findByEmail("mentor@example.com")
                .filter(m -> m.getUser() == null)
                .ifPresent(m -> {
                    m.linkUser(ensureLogin(m.getEmail(), Role.MENTOR));
                    mentorRepository.save(m);
                });
        studentRepository.findByEmail("student1@example.com")
                .filter(s -> s.getUser() == null)
                .ifPresent(s -> {
                    s.linkUser(ensureLogin(s.getEmail(), Role.STUDENT));
                    studentRepository.save(s);
                });

        log.info("Dev logins (password '{}'): admin@example.com, mentor@example.com, student1@example.com",
                DEV_PASSWORD);
    }

    private AppUser ensureLogin(String email, Role role) {
        return appUserRepository.findByEmail(email).orElseGet(() ->
                appUserRepository.save(new AppUser(email, passwordEncoder.encode(DEV_PASSWORD), role)));
    }
}