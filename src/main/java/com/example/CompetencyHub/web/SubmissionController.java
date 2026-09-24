package com.example.CompetencyHub.web;

import com.example.CompetencyHub.domain.enums.SubmissionStatus;
import com.example.CompetencyHub.domain.model.Submission;
import com.example.CompetencyHub.service.SubmissionService;
import com.example.CompetencyHub.web.dto.request.GradeRequest;
import com.example.CompetencyHub.web.dto.request.SubmitRequest;
import com.example.CompetencyHub.web.dto.response.SubmissionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    /** 201 either way; the body's status says whether it was graded on the spot or queued. */
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
    @GetMapping("/assessments/{assessmentId}/submissions")
    public List<SubmissionResponse> listForAssessment(@PathVariable Long assessmentId,
                                                      @RequestParam(required = false) SubmissionStatus status) {
        return submissionService.findByAssessment(assessmentId, status).stream()
                .map(SubmissionResponse::from)
                .toList();
    }

    @GetMapping("/students/{studentId}/submissions")
    public List<SubmissionResponse> listForStudent(@PathVariable Long studentId) {
        return submissionService.findByStudent(studentId).stream()
                .map(SubmissionResponse::from)
                .toList();
    }

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
    @PostMapping("/submissions/{id}/grade")
    public SubmissionResponse grade(@PathVariable Long id, @Valid @RequestBody GradeRequest request) {
        return SubmissionResponse.from(submissionService.grade(
                id, request.mentorId(), request.score(), request.feedback()));
    }
}
