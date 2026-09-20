package com.example.CompetencyHub.web;

import com.example.CompetencyHub.domain.model.Enrollment;
import com.example.CompetencyHub.service.EnrollmentService;
import com.example.CompetencyHub.web.dto.request.EnrollRequest;
import com.example.CompetencyHub.web.dto.response.EnrollmentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

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
    @PostMapping("/courses/{courseId}/enrollments")
    public ResponseEntity<EnrollmentResponse> enroll(@PathVariable Long courseId,
                                                     @RequestBody EnrollRequest request) {
        Enrollment enrollment = enrollmentService.enroll(request.studentId(), courseId);

        return ResponseEntity
                .created(URI.create("/api/enrollments/" + enrollment.getId()))
                .body(EnrollmentResponse.from(enrollment));
    }

    @DeleteMapping("/enrollments/{enrollmentId}")
    public ResponseEntity<Void> withdraw(@PathVariable Long enrollmentId) {
        enrollmentService.withdraw(enrollmentId);
        return ResponseEntity.noContent().build();   // 204
    }
}