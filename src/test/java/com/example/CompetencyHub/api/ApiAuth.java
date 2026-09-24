package com.example.CompetencyHub.api;

import com.example.CompetencyHub.domain.enums.Role;
import com.example.CompetencyHub.domain.model.AppUser;
import com.example.CompetencyHub.repository.AppUserRepository;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.springframework.security.crypto.password.PasswordEncoder;

import static io.restassured.RestAssured.given;

/**
 * Real tokens for integration tests, obtained the way a client would: through
 * /api/auth/register and /api/auth/login. That way every Rest Assured test also exercises
 * the login flow, the signing key, and the resource server's verification.
 *
 * <p>The one shortcut is the admin, inserted directly -- no endpoint creates admins, which is
 * the point.
 */
final class ApiAuth {

    static final String PASSWORD = "integration-test-pass";

    private ApiAuth() { }

    static String adminToken(AppUserRepository users, PasswordEncoder encoder) {
        String email = "admin@it.example.com";
        if (!users.existsByEmail(email)) {
            users.save(new AppUser(email, encoder.encode(PASSWORD), Role.ADMIN));
        }
        return login(email, PASSWORD);
    }

    /** Registers a student through the public endpoint. */
    static Registered registerStudent(String first, String last, String email) {
        JsonPath body = given().contentType(ContentType.JSON)
                .body("""
                        { "firstName": "%s", "lastName": "%s", "email": "%s", "password": "%s" }
                        """.formatted(first, last, email, PASSWORD))
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .extract().jsonPath();
        return new Registered(body.getString("accessToken"), body.getLong("studentId"));
    }

    /** An admin creates the mentor (with a password), then the mentor logs in. */
    static Registered createMentor(String adminToken, String email) {
        long mentorId = given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body("""
                        { "firstName": "Grace", "lastName": "Hopper", "email": "%s", "password": "%s" }
                        """.formatted(email, PASSWORD))
                .when().post("/api/mentors")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
        return new Registered(login(email, PASSWORD), mentorId);
    }

    static String login(String email, String password) {
        return given().contentType(ContentType.JSON)
                .body("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(email, password))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    /** A token plus the profile id it belongs to (studentId or mentorId). */
    record Registered(String token, long id) { }
}
