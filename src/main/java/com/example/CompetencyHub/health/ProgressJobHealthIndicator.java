package com.example.CompetencyHub.health;

import com.example.CompetencyHub.domain.model.JobRun;
import com.example.CompetencyHub.jobs.ProgressRecalculationService;
import com.example.CompetencyHub.repository.JobRunRepository;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * The health check that actually earns its place.
 *
 * "Is Postgres reachable" is worth knowing but the orchestrator would notice anyway.
 * "Did the nightly job succeed" is a silent failure: the app serves traffic perfectly
 * while every progress number on every screen slowly goes stale, and nobody finds out
 * until a student complains weeks later.
 *
 * A health indicator reporting on your own domain, not just your dependencies, is the
 * difference between monitoring the plumbing and monitoring the product.
 */
@Component("progressJob")
public class ProgressJobHealthIndicator implements HealthIndicator {

    // Generous: the job runs nightly, so 26 hours means it missed a full cycle plus slack.
    // Too tight and you page someone over a job that started twenty minutes late.
    private static final Duration STALE_AFTER = Duration.ofHours(26);

    private final JobRunRepository jobRunRepository;

    public ProgressJobHealthIndicator(JobRunRepository jobRunRepository) {
        this.jobRunRepository = jobRunRepository;
    }

    @Override
    public Health health() {
        return jobRunRepository
                .findFirstByJobNameAndStatusOrderByStartedAtDesc(
                        ProgressRecalculationService.JOB_NAME, JobRun.Status.SUCCEEDED)
                .map(this::assess)
                // No successful run ever. On a fresh database that is normal, not broken,
                // so UNKNOWN rather than DOWN -- a health check that cries wolf on every
                // new deployment gets ignored, and then it is worth nothing.
                .orElseGet(() -> Health.unknown()
                        .withDetail("reason", "no successful run recorded yet")
                        .build());
    }

    private Health assess(JobRun lastSuccess) {
        Duration age = Duration.between(lastSuccess.getFinishedAt(), Instant.now());
        boolean stale = age.compareTo(STALE_AFTER) > 0;

        return (stale ? Health.down() : Health.up())
                .withDetail("lastSuccessAt", lastSuccess.getFinishedAt())
                .withDetail("ageHours", age.toHours())
                .withDetail("recordsProcessed", lastSuccess.getRecordsProcessed())
                .withDetail("staleAfterHours", STALE_AFTER.toHours())
                .build();
    }
}
