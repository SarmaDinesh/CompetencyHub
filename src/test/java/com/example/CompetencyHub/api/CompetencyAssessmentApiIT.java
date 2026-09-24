package com.example.CompetencyHub.api;

import com.example.CompetencyHub.TestcontainersConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.*;

/**
 * The whole chain over real HTTP and real Postgres: course -> competency -> both kinds of
 * assessment. The things only this level can prove:
 *
 *   - JOINED inheritance really writes two rows and reads them back as the right subtype
 *   - the V3 schema accepts what the DTOs allow (nullable rubric_url and word_limit)
 *   - the delete guards line up with the real foreign keys
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class CompetencyAssessmentApiIT {

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
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
    void buildACourseStructureAndReadItBack() {
        int courseId = createCourse("CS544");

        int first = createCompetency(courseId, """
                { "title": "Transactions", "weight": 50 }
                """);
        int second = createCompetency(courseId, """
                { "title": "Messaging", "weight": 50 }
                """);

        // orderIndex was omitted both times, so they were appended 1, 2.
        when().get("/api/courses/{id}/competencies", courseId)
                .then().statusCode(200)
                .body("id", contains(first, second))
                .body("orderIndex", contains(1, 2));

        createAssessment(first, """
                { "type": "OBJECTIVE", "title": "Quiz 1", "minScore": 0, "maxScore": 100,
                  "questionCount": 20, "passingScore": 70 }
                """);
        createAssessment(first, """
                { "type": "PERFORMANCE", "title": "Design essay", "minScore": 0, "maxScore": 100,
                  "passingScore": 60 }
                """);

        // Read back through a polymorphic query: each row returns as its own subtype, and
        // the optional performance fields came back as null, not 0.
        when().get("/api/competencies/{id}/assessments", first)
                .then().log().ifValidationFails()
                .statusCode(200)
                .body("type", contains("OBJECTIVE", "PERFORMANCE"))
                .body("[0].passingScore", equalTo(70))
                .body("[1].wordLimit", nullValue())
                .body("[1].format", equalTo("Performance task, graded by rubric, pass at 60"));
    }

    @Test
    void competenciesAndAssessmentsWithSubmissionsCannotBeDeleted() {
        int courseId = createCourse("CS545");
        int competencyId = createCompetency(courseId, """
                { "title": "HTTP", "weight": 100 }
                """);
        int assessmentId = createAssessment(competencyId, """
                { "type": "OBJECTIVE", "title": "Quiz", "minScore": 0, "maxScore": 100,
                  "questionCount": 10, "passingScore": 60 }
                """);

        // Written directly rather than through the API: this test is about the delete guard,
        // not about enrollment and submission rules.
        jdbcTemplate.update("""
                INSERT INTO student (first_name, last_name, email, joined_on)
                VALUES ('Ada', 'Lovelace', 'ada@example.com', current_date)
                """);
        // V9 columns: a graded row needs status, attempt number and graded_at (the CHECK
        // constraint enforces that a GRADED row has them).
        jdbcTemplate.update("""
                INSERT INTO submission (student_id, assessment_id, score, submitted_at,
                                        status, attempt_number, graded_at)
                VALUES (1, ?, 90, now(), 'GRADED', 1, now())
                """, assessmentId);

        when().delete("/api/assessments/{id}", assessmentId)
                .then().statusCode(409)
                .body("detail", containsString("submissions"));

        when().delete("/api/competencies/{id}", competencyId)
                .then().statusCode(409);

        // Both still there.
        when().get("/api/assessments/{id}", assessmentId).then().statusCode(200);
        when().get("/api/competencies/{id}", competencyId).then().statusCode(200);
    }

    @Test
    void deletingACompetencyAlsoRemovesItsAssessments() {
        int courseId = createCourse("CS546");
        int competencyId = createCompetency(courseId, """
                { "title": "Scheduling", "weight": 100 }
                """);
        int assessmentId = createAssessment(competencyId, """
                { "type": "PERFORMANCE", "title": "Cron lab", "minScore": 0, "maxScore": 10,
                  "passingScore": 7 }
                """);

        when().delete("/api/competencies/{id}", competencyId)
                .then().statusCode(204);

        // Gone via the database's ON DELETE CASCADE, not Java code -- which is why this
        // needs a real database to test.
        when().get("/api/assessments/{id}", assessmentId).then().statusCode(404);
    }

    // ---- helpers ------------------------------------------------------------

    private int createCourse(String code) {
        return given().contentType(ContentType.JSON)
                .body("""
                        { "code": "%s", "title": "Course %s", "capacity": 10 }
                        """.formatted(code, code))
                .when().post("/api/courses")
                .then().statusCode(201)
                .extract().path("id");
    }

    private int createCompetency(int courseId, String body) {
        return given().contentType(ContentType.JSON).body(body)
                .when().post("/api/courses/{id}/competencies", courseId)
                .then().log().ifValidationFails().statusCode(201)
                .extract().path("id");
    }

    private int createAssessment(int competencyId, String body) {
        return given().contentType(ContentType.JSON).body(body)
                .when().post("/api/competencies/{id}/assessments", competencyId)
                .then().log().ifValidationFails().statusCode(201)
                .extract().path("id");
    }
}
