package com.example.CompetencyHub.web;

import com.example.CompetencyHub.domain.model.Enrollment;
import com.example.CompetencyHub.service.EnrollmentService;
import com.example.CompetencyHub.web.dto.request.EnrollRequest;
import com.example.CompetencyHub.web.dto.response.EnrollmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Tag(name = "Enrollments")
@RestController
@RequestMapping("/api")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    /**
     * Enrollment is modelled as a sub-resource of the course, which is why the path is
     * /courses/{id}/enrollments rather than /enroll. REST paths name things, not actions;
     * the verb is the HTTP method.
     *
     * <p>Returns 201 with a Location header — the correct response for creating a
     * resource, and something an API client can actually follow.
     */
    /*
     * @Valid is what makes the @NotNull on EnrollRequest.studentId actually run.
     * Constraint annotations on a record are only declarations; nothing checks them until
     * something asks. Without @Valid, a body of {} reached the service with a null id,
     * findById(null) threw IllegalArgumentException, and the client got a 500 -- a client
     * mistake reported as a server failure. Now it is a 400 with fieldErrors.studentId.
     */
    @Operation(summary = "Enroll a student in a course")
    @ApiResponse(responseCode = "201", description = "Enrolled; one seat reserved")
    @ApiResponse(responseCode = "400", description = "studentId missing")
    @ApiResponse(responseCode = "404", description = "No such student or course")
    @ApiResponse(responseCode = "409", description = "Course full, already enrolled or completed, or the last seat was taken concurrently")
    @PostMapping("/courses/{courseId}/enrollments")
    public ResponseEntity<EnrollmentResponse> enroll(@PathVariable Long courseId,
                                                     @Valid @RequestBody EnrollRequest request) {
        Enrollment enrollment = enrollmentService.enroll(request.studentId(), courseId);

        return ResponseEntity
                .created(URI.create("/api/enrollments/" + enrollment.getId()))
                .body(EnrollmentResponse.from(enrollment));
    }

    @Operation(summary = "Withdraw from a course")
    @ApiResponse(responseCode = "204", description = "Withdrawn; the seat is released")
    @ApiResponse(responseCode = "404", description = "No enrollment with this id")
    @ApiResponse(responseCode = "409", description = "The enrollment is not ACTIVE")
    @DeleteMapping("/enrollments/{enrollmentId}")
    public ResponseEntity<Void> withdraw(@PathVariable Long enrollmentId) {
        enrollmentService.withdraw(enrollmentId);
        return ResponseEntity.noContent().build();   // 204
    }
}