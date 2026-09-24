package com.example.CompetencyHub.web;

import com.example.CompetencyHub.domain.model.Competency;
import com.example.CompetencyHub.service.CompetencyService;
import com.example.CompetencyHub.web.dto.request.CreateCompetencyRequest;
import com.example.CompetencyHub.web.dto.request.UpdateCompetencyRequest;
import com.example.CompetencyHub.web.dto.response.CompetencyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Two URL shapes, and the difference is intentional:
 *
 * <ul>
 *   <li>{@code /api/courses/{courseId}/competencies} -- the COLLECTION belongs to a course,
 *       so listing and creating go through the parent. You cannot create a competency
 *       without saying which course it is in.</li>
 *   <li>{@code /api/competencies/{id}} -- once it exists, a competency has its own identity.
 *       Nesting it further ({@code /courses/1/competencies/7}) would force every client to
 *       know the course id just to read one competency, and invite requests where the two
 *       ids disagree.</li>
 * </ul>
 *
 * "Nest to create and list, flat to address one" is the common REST convention.
 */
@Tag(name = "Competencies")
@RestController
@RequestMapping("/api")
public class CompetencyController {

    private final CompetencyService competencyService;

    public CompetencyController(CompetencyService competencyService) {
        this.competencyService = competencyService;
    }

    @Operation(summary = "List a course's competencies")
    @ApiResponse(responseCode = "200", description = "Competencies in order")
    @ApiResponse(responseCode = "404", description = "No course with this id")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/courses/{courseId}/competencies")
    public List<CompetencyResponse> listForCourse(@PathVariable Long courseId) {
        return competencyService.findByCourse(courseId).stream()
                .map(CompetencyResponse::from)
                .toList();
    }

    @Operation(summary = "Add a competency to a course",
            description = "orderIndex is optional: omitted means after the last one.")
    @ApiResponse(responseCode = "201", description = "Created; Location points at /api/competencies/{id}")
    @ApiResponse(responseCode = "400", description = "Invalid body")
    @ApiResponse(responseCode = "404", description = "No course with this id")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/courses/{courseId}/competencies")
    public ResponseEntity<CompetencyResponse> create(@PathVariable Long courseId,
                                                     @Valid @RequestBody CreateCompetencyRequest request,
                                                     UriComponentsBuilder uriBuilder) {
        Competency created = competencyService.create(
                courseId, request.title(), request.weight(), request.orderIndex());

        URI location = uriBuilder.path("/api/competencies/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(CompetencyResponse.from(created));
    }

    @Operation(summary = "Get a competency")
    @ApiResponse(responseCode = "200", description = "The competency")
    @ApiResponse(responseCode = "404", description = "No competency with this id")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/competencies/{id}")
    public CompetencyResponse findById(@PathVariable Long id) {
        return CompetencyResponse.from(competencyService.findById(id));
    }

    @Operation(summary = "Replace a competency's editable fields")
    @ApiResponse(responseCode = "200", description = "The updated competency")
    @ApiResponse(responseCode = "400", description = "Invalid body")
    @ApiResponse(responseCode = "404", description = "No competency with this id")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/competencies/{id}")
    public CompetencyResponse update(@PathVariable Long id,
                                     @Valid @RequestBody UpdateCompetencyRequest request) {
        return CompetencyResponse.from(competencyService.update(
                id, request.title(), request.weight(), request.orderIndex()));
    }

    @Operation(summary = "Delete a competency and its assessments")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No competency with this id")
    @ApiResponse(responseCode = "409", description = "Students have submitted work against its assessments")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/competencies/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        competencyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
