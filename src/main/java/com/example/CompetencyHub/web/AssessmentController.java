package com.example.CompetencyHub.web;

import com.example.CompetencyHub.domain.model.Assessment;
import com.example.CompetencyHub.service.AssessmentService;
import com.example.CompetencyHub.web.dto.request.CreateAssessmentRequest;
import com.example.CompetencyHub.web.dto.request.CreateObjectiveAssessmentRequest;
import com.example.CompetencyHub.web.dto.request.CreatePerformanceAssessmentRequest;
import com.example.CompetencyHub.web.dto.response.AssessmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Same URL convention as CompetencyController: nested to create and list, flat to address one. */
@Tag(name = "Assessments")
@RestController
@RequestMapping("/api")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @Operation(summary = "List a competency's assessments")
    @ApiResponse(responseCode = "200", description = "Each item carries its own type: OBJECTIVE or PERFORMANCE")
    @ApiResponse(responseCode = "404", description = "No competency with this id")
    @GetMapping("/competencies/{competencyId}/assessments")
    public List<AssessmentResponse> listForCompetency(@PathVariable Long competencyId) {
        return assessmentService.findByCompetency(competencyId).stream()
                .map(AssessmentResponse::from)
                .toList();
    }

    /**
     * Declared as the sealed interface. Jackson reads "type", builds the matching record,
     * and @Valid then checks that record's own constraints.
     *
     * <p>Translating the request into a service call is the web layer's job (same as
     * CourseController unpacking CreateCourseRequest), so the type dispatch lives here.
     */
    @Operation(summary = "Create an objective test or a performance task",
            description = "The type field selects the body shape. See the two examples.")
    @ApiResponse(responseCode = "201", description = "Created; Location points at /api/assessments/{id}")
    @ApiResponse(responseCode = "400", description = "Missing or unknown type, or invalid fields for that type")
    @ApiResponse(responseCode = "404", description = "No competency with this id")
    @PostMapping("/competencies/{competencyId}/assessments")
    public ResponseEntity<AssessmentResponse> create(
            @PathVariable Long competencyId,
            /*
             * Fully qualified on purpose: this is Swagger's @RequestBody (documentation), and
             * the plain @RequestBody below is Spring's (binding). Same simple name, different
             * jobs -- importing both would not compile. The examples appear as a drop-down in
             * Swagger UI, one per assessment type.
             */
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    examples = {
                            @ExampleObject(name = "OBJECTIVE", summary = "Objective test", value = """
                                    { "type": "OBJECTIVE", "title": "Quiz 1", "minScore": 0, "maxScore": 100,
                                      "questionCount": 20, "passingScore": 70 }"""),
                            @ExampleObject(name = "PERFORMANCE", summary = "Performance task", value = """
                                    { "type": "PERFORMANCE", "title": "Design essay", "minScore": 0,
                                      "maxScore": 100, "passingScore": 60,
                                      "rubricUrl": "https://example.com/rubric", "wordLimit": 1500 }""")
                    }))
            @Valid @RequestBody CreateAssessmentRequest request,
            UriComponentsBuilder uriBuilder) {
        /*
         * Pattern matching for switch (Java 21). Each case tests the type AND binds a typed
         * variable, so there is no cast. No default branch: CreateAssessmentRequest is
         * sealed, the compiler knows these two cases are all of them, and adding a third
         * subtype turns this switch into a compile error until it is handled.
         *
         * The old way, for comparison:
         *
         *   Assessment created;
         *   if (request instanceof CreateObjectiveAssessmentRequest) {
         *       CreateObjectiveAssessmentRequest o = (CreateObjectiveAssessmentRequest) request;
         *       created = assessmentService.createObjective(...);
         *   } else if (request instanceof CreatePerformanceAssessmentRequest) {
         *       ...
         *   } else {
         *       throw new IllegalArgumentException("Unknown type");   // hope it never runs
         *   }
         */
        Assessment created = switch (request) {
            case CreateObjectiveAssessmentRequest o -> assessmentService.createObjective(
                    competencyId, o.title(), o.minScore(), o.maxScore(),
                    o.questionCount(), o.passingScore());
            case CreatePerformanceAssessmentRequest p -> assessmentService.createPerformance(
                    competencyId, p.title(), p.minScore(), p.maxScore(),
                    p.passingScore(), p.rubricUrl(), p.wordLimit());
        };

        URI location = uriBuilder.path("/api/assessments/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(AssessmentResponse.from(created));
    }

    @Operation(summary = "Get an assessment")
    @ApiResponse(responseCode = "200", description = "The assessment")
    @ApiResponse(responseCode = "404", description = "No assessment with this id")
    @GetMapping("/assessments/{id}")
    public AssessmentResponse findById(@PathVariable Long id) {
        return AssessmentResponse.from(assessmentService.findById(id));
    }

    @Operation(summary = "Delete an assessment")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No assessment with this id")
    @ApiResponse(responseCode = "409", description = "Students have submitted work against it")
    @DeleteMapping("/assessments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        assessmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
