package com.example.CompetencyHub.web;

import com.example.CompetencyHub.domain.model.JobRun;
import com.example.CompetencyHub.jobs.JobLauncher;
import com.example.CompetencyHub.jobs.ProgressRecalculationService;
import com.example.CompetencyHub.repository.JobRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin/jobs")
public class AdminJobController {

    private final JobLauncher jobLauncher;
    private final JobRunRepository jobRunRepository;

    public AdminJobController(JobLauncher jobLauncher, JobRunRepository jobRunRepository) {
        this.jobLauncher = jobLauncher;
        this.jobRunRepository = jobRunRepository;
    }

    /**
     * 202 Accepted, not 200 OK. The distinction is real and it is the correct status for
     * exactly this: the request was valid and work has been started, but it is not finished
     * and there is no result to return yet. Returning 200 with an empty body would claim the
     * work is done.
     *
     * The future is deliberately not waited on. Blocking here would defeat the @Async
     * entirely and hold the HTTP thread for the length of the job.
     */
    @Operation(summary = "Run the progress recalculation job now")
    @ApiResponse(responseCode = "202", description = "Started in the background; poll the runs endpoint for the outcome")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/progress-recalculation")
    public ResponseEntity<Void> triggerProgressRecalculation() {
        jobLauncher.launchProgressRecalculation();
        return ResponseEntity.accepted().build();
    }

    /** How you find out what happened, since the trigger above tells you nothing. */
    @Operation(summary = "Recent runs of the progress recalculation job")
    @ApiResponse(responseCode = "200", description = "Up to 20 runs, newest first")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/progress-recalculation/runs")
    public List<JobRunResponse> recentRuns() {
        return jobRunRepository
                .findByJobNameOrderByStartedAtDesc(
                        ProgressRecalculationService.JOB_NAME, PageRequest.of(0, 20))
                .map(JobRunResponse::from)
                .getContent();
    }

    public record JobRunResponse(Long id, String status, String startedAt,
                                 String finishedAt, int recordsProcessed, String detail) {
        static JobRunResponse from(JobRun r) {
            return new JobRunResponse(
                    r.getId(), r.getStatus().name(), String.valueOf(r.getStartedAt()),
                    String.valueOf(r.getFinishedAt()), r.getRecordsProcessed(), r.getDetail());
        }
    }
}
