package com.example.CompetencyHub.api;

import com.example.CompetencyHub.TestcontainersConfig;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the generated OpenAPI document itself.
 *
 * <p>Code-first docs cannot drift from the code's URLs -- but they CAN drift from the
 * intent: a new endpoint added without {@code @Operation} still appears, with no summary
 * and no error codes, and nobody notices until a client author asks. This test makes
 * "every endpoint is documented" a build rule rather than a hope.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class OpenApiDocsIT {

    private static final List<String> HTTP_METHODS = List.of("get", "post", "put", "patch", "delete");

    @LocalServerPort private int port;

    private JsonPath spec;

    @BeforeEach
    void loadSpec() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        spec = get("/v3/api-docs").then().statusCode(200).extract().jsonPath();
    }

    @Test
    void everyOperationHasASummaryASuccessResponseAndProblemDetailErrors() {
        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, Map<String, Object>> path : paths().entrySet()) {
            for (String method : HTTP_METHODS) {
                @SuppressWarnings("unchecked")
                Map<String, Object> operation = (Map<String, Object>) path.getValue().get(method);
                if (operation == null) continue;
                String name = method.toUpperCase() + " " + path.getKey();

                if (operation.get("summary") == null) {
                    problems.add(name + ": no summary");
                }

                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> responses =
                        (Map<String, Map<String, Object>>) operation.get("responses");
                if (responses.keySet().stream().noneMatch(code -> code.startsWith("2"))) {
                    problems.add(name + ": no 2xx response documented");
                }
                responses.forEach((code, response) -> {
                    if ((code.startsWith("4") || code.startsWith("5"))
                            && !String.valueOf(response.get("content")).contains("application/problem+json")) {
                        problems.add(name + " " + code + ": error body is not application/problem+json");
                    }
                });
            }
        }

        // All failures at once, not the first -- same reasoning as returning every field error.
        assertThat(problems).as("undocumented or mis-documented operations").isEmpty();
    }

    @Test
    void theResumeClaimHolds() {
        long operations = paths().values().stream()
                .mapToLong(item -> item.keySet().stream().filter(HTTP_METHODS::contains).count())
                .sum();

        // "25+ documented REST endpoints". Deliberately >= rather than an exact count, so adding
        // an endpoint does not break this test -- removing enough of them does.
        assertThat(operations).isGreaterThanOrEqualTo(25);
    }

    @Test
    void temporaryDiagnosticsAreNotPartOfTheContract() {
        assertThat(paths().keySet()).noneMatch(p -> p.startsWith("/api/diagnostics"));
    }

    @Test
    void assessmentCreationIsDocumentedAsAOneOfWithADiscriminator() {
        Map<String, Object> schema = spec.getMap("components.schemas.CreateAssessmentRequest");

        assertThat((List<?>) schema.get("oneOf")).hasSize(2);
        assertThat(spec.getString("components.schemas.CreateAssessmentRequest.discriminator.propertyName"))
                .isEqualTo("type");
    }

    @Test
    void pagingParametersAreExpandedForTheCourseList() {
        List<String> names = spec.getList("paths.'/api/courses'.get.parameters.name");

        // @ParameterObject at work: three real query parameters, not one opaque "pageable".
        assertThat(names).contains("page", "size", "sort", "search").doesNotContain("pageable");
    }

    @Test
    void swaggerUiIsServed() {
        get("/swagger-ui/index.html").then().statusCode(200);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> paths() {
        return (Map<String, Map<String, Object>>) (Map<?, ?>) spec.getMap("paths");
    }
}
