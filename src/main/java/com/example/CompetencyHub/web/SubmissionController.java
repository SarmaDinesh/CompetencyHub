package com.example.CompetencyHub.web;

import com.example.CompetencyHub.domain.enums.SubmissionStatus;
import com.example.CompetencyHub.domain.model.Submission;
import com.example.CompetencyHub.service.SubmissionService;
import com.example.CompetencyHub.web.dto.request.GradeRequest;
import com.example.CompetencyHub.web.dto.request.SubmitRequest;
import com.example.CompetencyHub.web.dto.response.SubmissionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Tag(name = "Submissions")
@RestController
@RequestMapping("/api")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    /** 201 either way; the body's status says whether it was graded on the spot or queued. */
    @Operation(summary = "Submit an attempt",
            description = "OBJECTIVE requires score. PERFORMANCE requires content and must not include score.")
    @ApiResponse(responseCode = "201", description = "OBJECTIVE: graded at once (status GRADED). PERFORMANCE: queued (status SUBMITTED)")
    @ApiResponse(responseCode = "400", description = "Wrong fields for the assessment type, score out of range, or over the word limit")
    @ApiResponse(responseCode = "404", description = "No such assessment or student")
    @ApiResponse(responseCode = "409", description = "The student is not actively enrolled in the course")
    @PostMapping("/assessments/{assessmentId}/submissions")
    public ResponseEntity<SubmissionResponse> submit(@PathVariable Long assessmentId,
                                                     @Valid @RequestBody SubmitRequest request,
                                                     UriComponentsBuilder uriBuilder) {
        Submission created = submissionService.submit(
                assessmentId, request.studentId(), request.score(), request.content());
        return ResponseEntity
                .created(uriBuilder.path("/api/submissions/{id}").buildAndExpand(created.getId()).toUri())
                .body(SubmissionResponse.from(created));
    }

    /**
     * Every submission for an assessment, or only the grading queue with ?status=SUBMITTED.
     *
     * <p>An enum as a request parameter: Spring converts "SUBMITTED" to the enum constant, and
     * a value that is not one ("?status=PENDING") fails conversion -- a 400 from the handler,
     * never reaching the service.
     */
    @Operation(summary = "List submissions for an assessment")
    @ApiResponse(responseCode = "200", description = "Oldest first. ?status=SUBMITTED gives the grading queue")
    @ApiResponse(responseCode = "400", description = "Unknown status value")
    @ApiResponse(responseCode = "404", description = "No assessment with this id")
    @GetMapping("/assessments/{assessmentId}/submissions")
    public List<SubmissionResponse> listForAssessment(@PathVariable Long assessmentId,
                                                      @Parameter(description = "Filter by status; SUBMITTED is the grading queue")
                                                      @RequestParam(required = false) SubmissionStatus status) {
        return submissionService.findByAssessment(assessmentId, status).stream()
                .map(SubmissionResponse::from)
                .toList();
    }

    @Operation(summary = "List a student's submissions")
    @ApiResponse(responseCode = "200", description = "Newest first")
    @ApiResponse(responseCode = "404", description = "No student with this id")
    @GetMapping("/students/{studentId}/submissions")
    public List<SubmissionResponse> listForStudent(@PathVariable Long studentId) {
        return submissionService.findByStudent(studentId).stream()
                .map(SubmissionResponse::from)
                .toList();
    }

    @Operation(summary = "Get a submission")
    @ApiResponse(responseCode = "200", description = "The submission")
    @ApiResponse(responseCode = "404", description = "No submission with this id")
    @GetMapping("/submissions/{id}")
    public SubmissionResponse findById(@PathVariable Long id) {
        return SubmissionResponse.from(submissionService.findById(id));
    }

    /**
     * Grading. "grade" is the one path segment here that reads like a verb, so it is worth
     * saying why it is still acceptable REST: read it as a noun -- POST creates the GRADE of
     * this submission, a thing that exists exactly once. Hence POST (not idempotent: a second
     * call is a 409, the grade already exists) and 200 with the updated submission.
     *
     * <p>The pure alternative is PATCH /api/submissions/{id} with {"status":"GRADED",...},
     * which hides a state transition with its own rules inside a generic field update. A named
     * sub-resource for a meaningful transition is the widely used compromise (GitHub's
     * "POST /pulls/{n}/merge" is the same shape).
     */
    @Operation(summary = "Grade a submission")
    @ApiResponse(responseCode = "200", description = "Graded; notification and progress follow asynchronously")
    @ApiResponse(responseCode = "400", description = "Invalid body or score outside the assessment's range")
    @ApiResponse(responseCode = "404", description = "No such submission or mentor")
    @ApiResponse(responseCode = "409", description = "Already graded, or graded concurrently by another mentor")
    @PostMapping("/submissions/{id}/grade")
    public SubmissionResponse grade(@PathVariable Long id, @Valid @RequestBody GradeRequest request) {
        return SubmissionResponse.from(submissionService.grade(
                id, request.mentorId(), request.score(), request.feedback()));
    }
}
