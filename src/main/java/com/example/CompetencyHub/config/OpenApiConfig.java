package com.example.CompetencyHub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * The OpenAPI document's metadata, and one rule applied to every endpoint.
 *
 * <p><b>Code-first vs contract-first.</b> This project is code-first: springdoc scans the
 * controllers at startup and GENERATES the spec from mappings, DTO records and validation
 * annotations. Contract-first is the reverse -- write openapi.yaml by hand, generate
 * controller interfaces from it. Contract-first suits teams where the frontend starts before
 * the backend exists; code-first suits a single team, and cannot drift from the code, because
 * it IS the code. The annotations on controllers only add what the code cannot say: summaries,
 * and which error codes an endpoint can return.
 *
 * <p><b>Old vs new.</b> Tutorials from the Spring Boot 2 era use Springfox and Swagger 2:
 * {@code @EnableSwagger2}, a {@code Docket} bean, {@code @Api}, {@code @ApiOperation},
 * {@code @ApiModelProperty}. Springfox stopped being maintained in 2020 and does not start on
 * Boot 3 or 4. springdoc replaced it, with the OpenAPI 3 annotations: {@code @Tag},
 * {@code @Operation}, {@code @Schema}, {@code @ApiResponse}. No enable annotation, no Docket --
 * adding the dependency is the switch.
 */
@Configuration
public class OpenApiConfig {

    static final String PROBLEM_SCHEMA = "ProblemDetail";
    static final String PROBLEM_MEDIA_TYPE = "application/problem+json";
    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI competencyHubOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        // Version from build-info (the goal already in the pom) so the docs always name the
        // build that is actually running. Absent when started from the IDE without a Maven
        // build, hence the fallback.
        String version = buildProperties.stream()
                .map(BuildProperties::getVersion)
                .findFirst()
                .orElse("dev");

        return new OpenAPI()
                .info(new Info()
                        .title("CompetencyHub API")
                        .version(version)
                        .description("""
                                Competency-based learning platform. Courses are made of \
                                competencies; each competency is proven by objective tests or \
                                mentor-graded performance tasks. Errors follow RFC 9457 \
                                (application/problem+json).""")
                        .contact(new Contact().name("CompetencyHub").url("https://github.com/SarmaDinesh/CompetencyHub"))
                        .license(new License().name("MIT")))
                // Declared here so Swagger UI shows the groups in THIS order, with descriptions,
                // rather than alphabetically with none.
                .tags(List.of(
                        new Tag().name("Auth").description("Register, log in, and inspect your token. Log in, then click Authorize and paste the accessToken."),
                        new Tag().name("Courses").description("Course catalog and seat capacity"),
                        new Tag().name("Competencies").description("The skills a course is made of"),
                        new Tag().name("Assessments").description("Objective tests and performance tasks that prove a competency"),
                        new Tag().name("Enrollments").description("Joining and leaving a course"),
                        new Tag().name("Submissions").description("Attempts at assessments, and grading"),
                        new Tag().name("Mentors").description("People who grade performance tasks"),
                        new Tag().name("Notifications").description("Messages produced by enrollment and grading events"),
                        new Tag().name("Admin").description("Operational endpoints: scheduled jobs")))
                // Every operation requires a bearer token unless it opts out with an empty
                // @SecurityRequirements (register, login). This is what puts the "Authorize"
                // button in Swagger UI: paste a token once and Try-it-out sends it everywhere.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSchemas(PROBLEM_SCHEMA, problemDetailSchema())
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token from POST /api/auth/login")));
    }

    /**
     * Documents security per operation, from the code that enforces it.
     *
     * <ul>
     *   <li>Every operation that needs a token gets a 401.</li>
     *   <li>Every method with {@code @PreAuthorize} gets a 403, and its description says who is
     *       allowed -- the expression itself, read off the annotation. The docs cannot claim
     *       "admins only" while the code says otherwise, because the docs ARE the code.</li>
     * </ul>
     */
    @Bean
    public OperationCustomizer securityResponses() {
        return (operation, handlerMethod) -> {
            boolean isPublic = handlerMethod.hasMethodAnnotation(SecurityRequirements.class);
            ApiResponses responses = ensureResponses(operation);

            if (!isPublic) {
                responses.computeIfAbsent("401",
                        code -> new ApiResponse().description("Missing, invalid or expired bearer token"));
            }

            PreAuthorize rule = handlerMethod.getMethodAnnotation(PreAuthorize.class);
            if (rule != null) {
                responses.computeIfAbsent("403",
                        code -> new ApiResponse().description("Authenticated, but not permitted"));
                String existing = operation.getDescription() == null ? "" : operation.getDescription() + "\n\n";
                operation.setDescription(existing + "**Access:** `" + rule.value() + "`");
            }
            return operation;
        };
    }

    /**
     * Every 4xx/5xx response in the spec gets the same body: our ProblemDetail.
     *
     * <p>Controllers declare WHICH errors an endpoint can return ({@code @ApiResponse(responseCode
     * = "404", description = "...")}); this customizer supplies WHAT the body looks like, once.
     * Otherwise every one of ~60 error responses would repeat the same six-line {@code @Content}
     * block, and one would inevitably be wrong.
     *
     * <p>It also adds a 500 to every operation. Any endpoint can fail unexpectedly; documenting
     * it tells client authors the error shape to expect even then.
     */
    @Bean
    public OpenApiCustomizer problemDetailResponses() {
        return openApi -> {
            if (openApi.getPaths() == null) return;
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                ApiResponses responses = ensureResponses(operation);
                responses.computeIfAbsent("500",
                        code -> new ApiResponse().description("Unexpected server error"));
                responses.forEach((code, response) -> {
                    if (code.startsWith("4") || code.startsWith("5")) {
                        response.setContent(new Content().addMediaType(PROBLEM_MEDIA_TYPE,
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA))));
                    }
                });
            }));
        };
    }

    private static ApiResponses ensureResponses(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        return operation.getResponses();
    }

    /**
     * Written by hand rather than generated from Spring's ProblemDetail class, because what
     * the client receives is not that class: GlobalExceptionHandler adds {@code timestamp} and,
     * for validation failures, {@code fieldErrors}. The schema documents the JSON on the wire.
     */
    private static Schema<?> problemDetailSchema() {
        return new ObjectSchema()
                .description("RFC 9457 problem details, as produced by GlobalExceptionHandler")
                .addProperty("type", new StringSchema().format("uri")
                        .example("https://competencyhub.example/errors/coursefull"))
                .addProperty("title", new StringSchema().example("Conflict"))
                .addProperty("status", new IntegerSchema().example(409))
                .addProperty("detail", new StringSchema().example("Course CS999 has no seats available"))
                .addProperty("instance", new StringSchema().format("uri-reference")
                        .example("/api/courses/11/enrollments"))
                .addProperty("timestamp", new DateTimeSchema())
                .addProperty("fieldErrors", new MapSchema()
                        .additionalProperties(new StringSchema())
                        .description("Present on 400 validation failures: field name -> message")
                        .example(java.util.Map.of("title", "Title is required")));
    }
}
