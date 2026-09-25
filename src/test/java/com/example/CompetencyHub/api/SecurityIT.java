package com.example.CompetencyHub.api;

import com.example.CompetencyHub.TestcontainersConfig;
import com.example.CompetencyHub.domain.enums.Role;
import com.example.CompetencyHub.domain.model.AppUser;
import com.example.CompetencyHub.repository.AppUserRepository;
import com.example.CompetencyHub.security.JwtConfig;
import com.example.CompetencyHub.security.JwtProperties;
import com.example.CompetencyHub.security.TokenService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.*;

/**
 * The security rules end to end: real filter chain, real signing key, real login.
 * Each test is one sentence of the security contract.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class SecurityIT {

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

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
                    assessment, competency, course, student, mentor, app_user, job_run
                RESTART IDENTITY CASCADE
                """);
        RestAssured.reset();
    }

    // ---- 401: who are you? ---------------------------------------------------------------

    @Test
    void noTokenIs401WithAProblemBodyAndABearerChallenge() {
        when().get("/api/courses")
                .then().statusCode(401)
                .contentType("application/problem+json")
                .header("WWW-Authenticate", startsWith("Bearer"))
                .body("status", equalTo(401));
    }

    @Test
    void aGarbageTokenIs401() {
        given().auth().oauth2("not.a.jwt")
                .when().get("/api/courses")
                .then().statusCode(401)
                .header("WWW-Authenticate", containsString("invalid_token"));
    }

    @Test
    void aTokenSignedWithAnotherKeyIs401() throws Exception {
        // A perfectly well-formed ADMIN token -- signed by a key this server has never seen.
        JwtConfig config = new JwtConfig();
        JwtProperties props = new JwtProperties("competencyhub", Duration.ofHours(1), null, null);
        TokenService forger = new TokenService(config.jwtEncoder(config.jwtSigningKey(props)), props, Clock.systemUTC());
        AppUser fakeAdmin = new AppUser("mallory@example.com", "x", Role.ADMIN);
        ReflectionTestUtils.setField(fakeAdmin, "id", 1L);

        given().auth().oauth2(forger.issue(fakeAdmin, null, null).value())
                .when().get("/api/admin/jobs/progress-recalculation/runs")
                .then().statusCode(401);
    }

    // ---- register / login ----------------------------------------------------------------

    @Test
    void registerLogsYouInAsAStudent() {
        String token = given().contentType(ContentType.JSON)
                .body("""
                        { "firstName": "Ada", "lastName": "Lovelace",
                          "email": "ada@example.com", "password": "correct-horse" }
                        """)
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .body("tokenType", equalTo("Bearer"))
                .body("role", equalTo("STUDENT"))
                .body("studentId", notNullValue())
                .extract().path("accessToken");

        given().auth().oauth2(token)
                .when().get("/api/auth/me")
                .then().statusCode(200)
                .body("email", equalTo("ada@example.com"))
                .body("roles", contains("STUDENT"));
    }

    @Test
    void theSameEmailCannotRegisterTwice() {
        ApiAuth.registerStudent("Ada", "Lovelace", "ada@example.com");

        given().contentType(ContentType.JSON)
                .body("""
                        { "firstName": "Ada", "lastName": "Again",
                          "email": "ada@example.com", "password": "another-pass" }
                        """)
                .when().post("/api/auth/register")
                .then().statusCode(409);
    }

    @Test
    void wrongPasswordAndUnknownEmailLookIdentical() {
        ApiAuth.registerStudent("Ada", "Lovelace", "ada@example.com");

        String wrongPassword = given().contentType(ContentType.JSON)
                .body("{ \"email\": \"ada@example.com\", \"password\": \"wrong-password\" }")
                .when().post("/api/auth/login")
                .then().statusCode(401).extract().path("detail");
        String unknownEmail = given().contentType(ContentType.JSON)
                .body("{ \"email\": \"nobody@example.com\", \"password\": \"wrong-password\" }")
                .when().post("/api/auth/login")
                .then().statusCode(401).extract().path("detail");

        // An attacker cannot use this endpoint to find out which emails have accounts.
        org.assertj.core.api.Assertions.assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    void passwordsAreStoredHashedNeverPlain() {
        ApiAuth.registerStudent("Ada", "Lovelace", "ada@example.com");

        String stored = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM app_user WHERE email = 'ada@example.com'", String.class);
        org.assertj.core.api.Assertions.assertThat(stored)
                .startsWith("{bcrypt}")
                .doesNotContain(ApiAuth.PASSWORD);
    }

    @Test
    void thereIsNoWayToRegisterAsAnAdmin() {
        // "role" is not part of RegisterRequest. Whether Jackson ignores the unknown field
        // (201, as a STUDENT) or rejects it (400), the outcome that matters is the same:
        // no ADMIN account exists afterwards.
        given().contentType(ContentType.JSON)
                .body("""
                        { "firstName": "Eve", "lastName": "Il", "email": "eve@example.com",
                          "password": "let-me-in-please", "role": "ADMIN" }
                        """)
                .when().post("/api/auth/register")
                .then().statusCode(anyOf(is(201), is(400)));

        Integer admins = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_user WHERE role = 'ADMIN'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(admins).isZero();
    }

    // ---- 403: what may you do? -------------------------------------------------------------

    @Test
    void aStudentCannotUseAdminEndpoints() {
        String student = ApiAuth.registerStudent("Ada", "Lovelace", "ada@example.com").token();

        given().auth().oauth2(student).contentType(ContentType.JSON)
                .body("{ \"code\": \"CS999\", \"title\": \"Mine now\", \"capacity\": 5 }")
                .when().post("/api/courses")
                .then().statusCode(403)
                .contentType("application/problem+json");

        // URL-level rule (SecurityConfig), not @PreAuthorize -- also 403.
        given().auth().oauth2(student)
                .when().get("/api/admin/jobs/progress-recalculation/runs")
                .then().statusCode(403);
    }

    @Test
    void studentsCannotReadEachOthersWork() {
        ApiAuth.Registered ada = ApiAuth.registerStudent("Ada", "Lovelace", "ada@example.com");
        ApiAuth.Registered alan = ApiAuth.registerStudent("Alan", "Turing", "alan@example.com");

        given().auth().oauth2(ada.token())
                .when().get("/api/students/{id}/submissions", ada.id())
                .then().statusCode(200);
        given().auth().oauth2(ada.token())
                .when().get("/api/students/{id}/submissions", alan.id())
                .then().statusCode(403);
    }

    @Test
    void anAdminCanDoAdminThings() {
        String admin = ApiAuth.adminToken(appUserRepository, passwordEncoder);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body("{ \"code\": \"CS999\", \"title\": \"Admin course\", \"capacity\": 5 }")
                .when().post("/api/courses")
                .then().statusCode(201);
    }

    // ---- what stays public ---------------------------------------------------------------

    @Test
    void healthIsPublicButItsDetailsAreNot() {
        // No Kafka broker in the test run: KafkaHealthIndicator is DOWN, so the aggregate is
        // DOWN and Actuator answers 503. Either code is fine here -- what this test protects
        // is that an anonymous caller sees a status and NO component details.
        when().get("/actuator/health")
                .then().statusCode(anyOf(is(200), is(503)))
                .body("status", notNullValue())
                .body("components", nullValue());
    }

    @Test
    void livenessIsUpEvenWhenADependencyIsDown() {
        // Liveness answers only "is this process alive?" -- not "is Kafka up?". If an
        // orchestrator used the aggregate /actuator/health to decide restarts, a Kafka outage
        // would restart every healthy app instance in a loop. This probe is what a container
        // health check should call; we'll use it in the Docker step.
        when().get("/actuator/health/liveness")
                .then().statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void aMistypedUrlIs404Not500() {
        String student = ApiAuth.registerStudent("Ada", "Lovelace", "ada@example.com").token();
        given().auth().oauth2(student)
                .when().get("/api/course/1")          // "course", not "courses"
                .then().statusCode(404);
    }

    @Test
    void apiDocsArePublic() {
        when().get("/v3/api-docs").then().statusCode(200);
    }
}
