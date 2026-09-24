package com.example.CompetencyHub.api;

import com.example.CompetencyHub.TestcontainersConfig;
import com.example.CompetencyHub.repository.AppUserRepository;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class CourseApiIT {

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // RANDOM_PORT so parallel CI builds don't fight over 8080. The port can't be a
        // constant, which is why this is @BeforeEach rather than the slides' @BeforeClass.
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";

        // Every request in this class acts as an admin. requestSpecification is STATIC: it
        // applies to every Rest Assured call in the JVM until reset -- hence the reset() in
        // @AfterEach, or the next test class would silently inherit this admin token.
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer " + ApiAuth.adminToken(appUserRepository, passwordEncoder))
                .build();
    }

    /**
     * These requests go over real HTTP and really commit -- there is no test transaction to
     * roll back. Without this, a course created by one test is still there for the next one,
     * `code` is UNIQUE, and the second test gets 409 instead of whatever it was asserting.
     *
     * Which test hits it depends on JUnit's method ordering, which is deterministic but not
     * source order. That is how a suite becomes "flaky": nothing is random, the tests just
     * depend on each other and nobody wrote down the dependency.
     */
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
    void createdCourseIsRetrievable() {
        Integer id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "code": "CS999",
                          "title": "End to end",
                          "description": "Created by the end-to-end test",
                          "capacity": 5
                        }
                        """)
                .when()
                .post("/api/courses")
                .then()
                .log().ifValidationFails()
                .statusCode(201)
                .extract().path("id");

        when()
                .get("/api/courses/{id}", id)
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body("code", equalTo("CS999"))
                .body("title", equalTo("End to end"))
                // A new course has every seat free.
                .body("seatsAvailable", equalTo(5));
    }

    @Test
    void validationFailureReturnsProblemDetail() {
        given()
                .contentType(ContentType.JSON)
                // Genuinely invalid: blank code, blank title, negative capacity. Nothing
                // here can collide with an existing row, so this test cannot accidentally
                // assert on a 409.
                .body("""
                        {
                          "code": "",
                          "title": "",
                          "description": "",
                          "capacity": -1
                        }
                        """)
                .when()
                .post("/api/courses")
                .then()
                .log().ifValidationFails()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }

    @Test
    void duplicateCodeReturns409() {
        String body = """
                {
                  "code": "CS998",
                  "title": "First",
                  "description": "The original",
                  "capacity": 10
                }
                """;

        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/courses")
                .then().statusCode(201);

        // Same code twice. The unique constraint makes this a genuine conflict: the request
        // is well formed but collides with existing state.
        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/courses")
                .then()
                .log().ifValidationFails()
                .statusCode(409)
                .contentType("application/problem+json")
                .body("type", containsString("duplicatecoursecode"));
    }

    @Test
    void unknownCourseReturns404() {
        when()
                .get("/api/courses/{id}", 999999)
                .then()
                .log().ifValidationFails()
                .statusCode(404)
                .body("status", equalTo(404));
    }
}