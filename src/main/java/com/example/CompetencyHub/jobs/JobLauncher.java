package com.example.CompetencyHub.jobs;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

@Service
public class JobLauncher {

    private final ProgressRecalculationService service;

    public JobLauncher(ProgressRecalculationService service) {
        this.service = service;
    }

    /**
     * Returning CompletableFuture rather than void so failures are observable. Spring unwraps
     * the returned future and completes the one it handed the caller, exceptionally if this
     * throws.
     *
     * The named executor matters. With no name, @Async uses the application-wide default
     * executor -- the same one an HTTP-adjacent async task would use -- and a long job can
     * starve it. A separate small pool for jobs means a stuck job cannot take anything else
     * down with it. This is the bulkhead pattern, at its smallest.
     */
    @Async("jobExecutor")
    public CompletableFuture<ProgressRecalculationService.JobResult> launchProgressRecalculation() {
        return CompletableFuture.completedFuture(service.recalculateAll());
    }
}
