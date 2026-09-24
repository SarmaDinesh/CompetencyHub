package com.example.CompetencyHub.api;

import com.example.CompetencyHub.TestcontainersConfig;
import com.example.CompetencyHub.domain.model.Student;
import com.example.CompetencyHub.messaging.EnrollmentEventPublisher;
import com.example.CompetencyHub.messaging.SubmissionEventPublisher;
import com.example.CompetencyHub.messaging.SubmissionGradedListener;
import com.example.CompetencyHub.messaging.event.SubmissionGradedEvent;
import com.example.CompetencyHub.repository.StudentRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.example.CompetencyHub.TestFixtures.student;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
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
    @Autowired private StudentRepository studentRepository;
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
    }

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
    void submitGradeNotifyAndProgress() {
        // ---- setup: one course, one competency, a quiz and an essay on it ---------------
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
        int mentorId = post("/api/mentors", """
                { "firstName": "Grace", "lastName": "Hopper", "email": "grace@example.com" }
                """);
        // No student endpoint yet (students arrive with registration in the security branch).
        Student ada = studentRepository.save(student("Ada", "Lovelace", "ada@example.com"));

        // ---- not enrolled -> 409 ---------------------------------------------------------
        given().contentType(ContentType.JSON)
                .body("{ \"studentId\": %d, \"score\": 85 }".formatted(ada.getId()))
                .when().post("/api/assessments/{id}/submissions", quizId)
                .then().statusCode(409)
                .body("detail", containsString("not actively enrolled"));

        post("/api/courses/" + courseId + "/enrollments", "{ \"studentId\": %d }".formatted(ada.getId()));

        // ---- objective: graded on submission --------------------------------------------
        given().contentType(ContentType.JSON)
                .body("{ \"studentId\": %d, \"score\": 55 }".formatted(ada.getId()))
                .when().post("/api/assessments/{id}/submissions", quizId)
                .then().log().ifValidationFails().statusCode(201)
                .body("status", equalTo("GRADED"))
                .body("attemptNumber", equalTo(1));

        // A second attempt gets the next number.
        given().contentType(ContentType.JSON)
                .body("{ \"studentId\": %d, \"score\": 85 }".formatted(ada.getId()))
                .when().post("/api/assessments/{id}/submissions", quizId)
                .then().statusCode(201)
                .body("attemptNumber", equalTo(2));

        // ---- performance: queued for a mentor -------------------------------------------
        int essaySubmissionId = given().contentType(ContentType.JSON)
                .body("{ \"studentId\": %d, \"content\": \"Two-phase commit is...\" }".formatted(ada.getId()))
                .when().post("/api/assessments/{id}/submissions", essayId)
                .then().statusCode(201)
                .body("status", equalTo("SUBMITTED"))
                .body("score", nullValue())
                .extract().path("id");

        when().get("/api/assessments/{id}/submissions?status=SUBMITTED", essayId)
                .then().statusCode(200)
                .body("id", contains(essaySubmissionId));

        // ---- grading --------------------------------------------------------------------
        given().contentType(ContentType.JSON)
                .body("{ \"mentorId\": %d, \"score\": 72, \"feedback\": \"Clear\" }".formatted(mentorId))
                .when().post("/api/submissions/{id}/grade", essaySubmissionId)
                .then().log().ifValidationFails().statusCode(200)
                .body("status", equalTo("GRADED"))
                .body("gradedByMentorId", equalTo(mentorId));

        // Again -> 409; and the queue is now empty.
        given().contentType(ContentType.JSON)
                .body("{ \"mentorId\": %d, \"score\": 90 }".formatted(mentorId))
                .when().post("/api/submissions/{id}/grade", essaySubmissionId)
                .then().statusCode(409);
        when().get("/api/assessments/{id}/submissions?status=SUBMITTED", essayId)
                .then().body("", empty());

        when().get("/api/students/{id}/submissions", ada.getId())
                .then().statusCode(200).body("", hasSize(3));

        // ---- post-grading work ----------------------------------------------------------
        // Two quiz attempts (auto) + one essay (mentor) = three events, all after commit.
        assertThat(recorder.events).hasSize(3);
        assertThat(recorder.events).filteredOn(SubmissionGradedEvent::autoGraded).hasSize(2);

        // Deliver them as Kafka would -- and deliver them all TWICE, as Kafka may.
        recorder.events.forEach(gradedListener::onSubmissionGraded);
        recorder.events.forEach(gradedListener::onSubmissionGraded);

        Integer notifications = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification WHERE event_type = 'SUBMISSION_GRADED'", Integer.class);
        assertThat(notifications).isEqualTo(3);   // not 6: the redelivery was deduplicated

        // One competency, mastered (quiz attempt 2 scored 85 >= 70): 100%.
        BigDecimal percent = jdbcTemplate.queryForObject(
                "SELECT percent_complete FROM progress WHERE student_id = ? AND course_id = ?",
                BigDecimal.class, ada.getId(), courseId);
        assertThat(percent).isEqualByComparingTo("100.00");
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
        Student ada = studentRepository.save(student("Ada", "Lovelace", "ada2@example.com"));
        post("/api/courses/" + courseId + "/enrollments", "{ \"studentId\": %d }".formatted(ada.getId()));

        // A student cannot score their own essay...
        given().contentType(ContentType.JSON)
                .body("{ \"studentId\": %d, \"score\": 100, \"content\": \"a b\" }".formatted(ada.getId()))
                .when().post("/api/assessments/{id}/submissions", essayId)
                .then().statusCode(400);

        // ...and cannot exceed the word limit.
        given().contentType(ContentType.JSON)
                .body("{ \"studentId\": %d, \"content\": \"one two three four\" }".formatted(ada.getId()))
                .when().post("/api/assessments/{id}/submissions", essayId)
                .then().statusCode(400)
                .body("detail", containsString("word limit"));

        // Neither rejected request consumed an attempt number.
        Integer rows = jdbcTemplate.queryForObject("SELECT count(*) FROM submission", Integer.class);
        assertThat(rows).isZero();
    }

    private int post(String path, String body) {
        return given().contentType(ContentType.JSON).body(body)
                .when().post(path)
                .then().log().ifValidationFails().statusCode(201)
                .extract().path("id");
    }
}
