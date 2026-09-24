package com.example.CompetencyHub.api;

import com.example.CompetencyHub.TestcontainersConfig;
import com.example.CompetencyHub.messaging.EnrollmentEventPublisher;
import com.example.CompetencyHub.messaging.SubmissionEventPublisher;
import com.example.CompetencyHub.messaging.SubmissionGradedListener;
import com.example.CompetencyHub.messaging.event.SubmissionGradedEvent;
import com.example.CompetencyHub.repository.AppUserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * The whole grading flow over HTTP against real Postgres, then the post-grading work.
 *
 * <p>Kafka is the one piece left out. The two Kafka publishers are mocked, and a test-only
 * listener records the in-process events instead -- registered with the SAME
 * {@code @TransactionalEventListener(AFTER_COMMIT)} as the real publisher, so it proves the
 * events fire only after commit. Those recorded events are then handed to the real consumer,
 * which is what Kafka would do with them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, SubmissionFlowIT.EventRecorderConfig.class})
class SubmissionFlowIT {

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SubmissionGradedListener gradedListener;
    @Autowired private EventRecorder recorder;

    @MockitoBean private EnrollmentEventPublisher enrollmentEventPublisher;
    @MockitoBean private SubmissionEventPublisher submissionEventPublisher;

    @TestConfiguration
    static class EventRecorderConfig {
        @Bean EventRecorder eventRecorder() { return new EventRecorder(); }
    }

    /** Collects SubmissionGradedEvents exactly when the real Kafka publisher would send them. */
    static class EventRecorder {
        final List<SubmissionGradedEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(SubmissionGradedEvent event) { events.add(event); }
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        recorder.events.clear();
        adminToken = ApiAuth.adminToken(appUserRepository, passwordEncoder);
    }

    /*
     * Three people, three tokens -- and each call below says whose hands it is in. That is the
     * point of this test after the security branch: the flow only works when each step is done
     * by the role allowed to do it.
     */
    private String adminToken;

    private static RequestSpecification as(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    progress, enrollment, enrollment_attempt, notification,
                    submission, objective_assessment, performance_assessment,
                    assessment, competency, course, student, mentor, app_user, job_run
                RESTART IDENTITY CASCADE
                """);
        RestAssured.reset();
    }

    @Test
    void submitGradeNotifyAndProgress() {
        // ---- setup (ADMIN): one course, one competency, a quiz and an essay on it --------
        int courseId = post("/api/courses", """
                { "code": "CS560", "title": "Grading Flow", "capacity": 10 }
                """);
        int competencyId = post("/api/courses/" + courseId + "/competencies", """
                { "title": "Transactions", "weight": 100 }
                """);
        int quizId = post("/api/competencies/" + competencyId + "/assessments", """
                { "type": "OBJECTIVE", "title": "Quiz", "minScore": 0, "maxScore": 100,
                  "questionCount": 10, "passingScore": 70 }
                """);
        int essayId = post("/api/competencies/" + competencyId + "/assessments", """
                { "type": "PERFORMANCE", "title": "Essay", "minScore": 0, "maxScore": 100,
                  "passingScore": 60 }
                """);

        // The student registers themselves; the admin creates the mentor.
        ApiAuth.Registered ada = ApiAuth.registerStudent("Ada", "Lovelace", "ada@example.com");
        ApiAuth.Registered grace = ApiAuth.createMentor(adminToken, "grace@example.com");

        // ---- not enrolled -> 409 ---------------------------------------------------------
        as(ada.token())
                .body("{ \"studentId\": %d, \"score\": 85 }".formatted(ada.id()))
                .when().post("/api/assessments/{id}/submissions", quizId)
                .then().statusCode(409)
                .body("detail", containsString("not actively enrolled"));

        // The student enrolls THEMSELVES, with their own token.
        as(ada.token())
                .body("{ \"studentId\": %d }".formatted(ada.id()))
                .when().post("/api/courses/{id}/enrollments", courseId)
                .then().statusCode(201);

        // ---- objective: graded on submission --------------------------------------------
        as(ada.token())
                .body("{ \"studentId\": %d, \"score\": 55 }".formatted(ada.id()))
                .when().post("/api/assessments/{id}/submissions", quizId)
                .then().log().ifValidationFails().statusCode(201)
                .body("status", equalTo("GRADED"))
                .body("attemptNumber", equalTo(1));

        as(ada.token())
                .body("{ \"studentId\": %d, \"score\": 85 }".formatted(ada.id()))
                .when().post("/api/assessments/{id}/submissions", quizId)
                .then().statusCode(201)
                .body("attemptNumber", equalTo(2));

        // ---- performance: queued for a mentor -------------------------------------------
        int essaySubmissionId = as(ada.token())
                .body("{ \"studentId\": %d, \"content\": \"Two-phase commit is...\" }".formatted(ada.id()))
                .when().post("/api/assessments/{id}/submissions", essayId)
                .then().statusCode(201)
                .body("status", equalTo("SUBMITTED"))
                .body("score", nullValue())
                .extract().path("id");

        // The student cannot see the grading queue; the mentor can.
        as(ada.token()).when().get("/api/assessments/{id}/submissions?status=SUBMITTED", essayId)
                .then().statusCode(403);
        as(grace.token()).when().get("/api/assessments/{id}/submissions?status=SUBMITTED", essayId)
                .then().statusCode(200)
                .body("id", contains(essaySubmissionId));

        // ---- grading (MENTOR) -----------------------------------------------------------
        // A student cannot grade their own essay, even naming the mentor's id.
        as(ada.token())
                .body("{ \"mentorId\": %d, \"score\": 100 }".formatted(grace.id()))
                .when().post("/api/submissions/{id}/grade", essaySubmissionId)
                .then().statusCode(403);

        as(grace.token())
                .body("{ \"mentorId\": %d, \"score\": 72, \"feedback\": \"Clear\" }".formatted(grace.id()))
                .when().post("/api/submissions/{id}/grade", essaySubmissionId)
                .then().log().ifValidationFails().statusCode(200)
                .body("status", equalTo("GRADED"))
                .body("gradedByMentorId", equalTo((int) grace.id()));

        as(grace.token())
                .body("{ \"mentorId\": %d, \"score\": 90 }".formatted(grace.id()))
                .when().post("/api/submissions/{id}/grade", essaySubmissionId)
                .then().statusCode(409);
        as(grace.token()).when().get("/api/assessments/{id}/submissions?status=SUBMITTED", essayId)
                .then().body("", empty());

        // The student reads their own history, and their own graded essay.
        as(ada.token()).when().get("/api/students/{id}/submissions", ada.id())
                .then().statusCode(200).body("", hasSize(3));
        as(ada.token()).when().get("/api/submissions/{id}", essaySubmissionId)
                .then().statusCode(200).body("feedback", equalTo("Clear"));

        // ---- post-grading work ----------------------------------------------------------
        assertThat(recorder.events).hasSize(3);
        assertThat(recorder.events).filteredOn(SubmissionGradedEvent::autoGraded).hasSize(2);

        recorder.events.forEach(gradedListener::onSubmissionGraded);
        recorder.events.forEach(gradedListener::onSubmissionGraded);

        Integer notifications = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification WHERE event_type = 'SUBMISSION_GRADED'", Integer.class);
        assertThat(notifications).isEqualTo(3);

        BigDecimal percent = jdbcTemplate.queryForObject(
                "SELECT percent_complete FROM progress WHERE student_id = ? AND course_id = ?",
                BigDecimal.class, ada.id(), courseId);
        assertThat(percent).isEqualByComparingTo("100.00");

        // And the notifications are the student's to read -- nobody else's.
        as(ada.token()).when().get("/api/notifications?recipient=ada@example.com")
                .then().statusCode(200).body("", hasSize(3));
        as(ada.token()).when().get("/api/notifications?recipient=someone-else@example.com")
                .then().statusCode(403);
    }

    @Test
    void wrongInputForTheAssessmentTypeIs400() {
        int courseId = post("/api/courses", """
                { "code": "CS561", "title": "Inputs", "capacity": 10 }
                """);
        int competencyId = post("/api/courses/" + courseId + "/competencies", """
                { "title": "HTTP", "weight": 100 }
                """);
        int essayId = post("/api/competencies/" + competencyId + "/assessments", """
                { "type": "PERFORMANCE", "title": "Essay", "minScore": 0, "maxScore": 100,
                  "passingScore": 60, "wordLimit": 3 }
                """);
        ApiAuth.Registered ada = ApiAuth.registerStudent("Ada", "Lovelace", "ada2@example.com");
        as(ada.token()).body("{ \"studentId\": %d }".formatted(ada.id()))
                .when().post("/api/courses/{id}/enrollments", courseId)
                .then().statusCode(201);

        // A student cannot score their own essay...
        as(ada.token())
                .body("{ \"studentId\": %d, \"score\": 100, \"content\": \"a b\" }".formatted(ada.id()))
                .when().post("/api/assessments/{id}/submissions", essayId)
                .then().statusCode(400);

        // ...and cannot exceed the word limit.
        as(ada.token())
                .body("{ \"studentId\": %d, \"content\": \"one two three four\" }".formatted(ada.id()))
                .when().post("/api/assessments/{id}/submissions", essayId)
                .then().statusCode(400)
                .body("detail", containsString("word limit"));

        // Neither rejected request consumed an attempt number.
        Integer rows = jdbcTemplate.queryForObject("SELECT count(*) FROM submission", Integer.class);
        assertThat(rows).isZero();
    }

    /** Admin setup call. */
    private int post(String path, String body) {
        return as(adminToken).body(body)
                .when().post(path)
                .then().log().ifValidationFails().statusCode(201)
                .extract().path("id");
    }
}
