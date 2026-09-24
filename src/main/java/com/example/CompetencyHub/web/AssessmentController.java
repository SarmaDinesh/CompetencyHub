package com.example.CompetencyHub.web;

import com.example.CompetencyHub.domain.model.Assessment;
import com.example.CompetencyHub.service.AssessmentService;
import com.example.CompetencyHub.web.dto.request.CreateAssessmentRequest;
import com.example.CompetencyHub.web.dto.request.CreateObjectiveAssessmentRequest;
import com.example.CompetencyHub.web.dto.request.CreatePerformanceAssessmentRequest;
import com.example.CompetencyHub.web.dto.response.AssessmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Same URL convention as CompetencyController: nested to create and list, flat to address one. */
@RestController
@RequestMapping("/api")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

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
    @PostMapping("/competencies/{competencyId}/assessments")
    public ResponseEntity<AssessmentResponse> create(@PathVariable Long competencyId,
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

    @GetMapping("/assessments/{id}")
    public AssessmentResponse findById(@PathVariable Long id) {
        return AssessmentResponse.from(assessmentService.findById(id));
    }

    @DeleteMapping("/assessments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        assessmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
