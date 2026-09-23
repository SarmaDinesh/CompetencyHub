package com.example.CompetencyHub.jobs;

import com.example.CompetencyHub.config.JobProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deliberately thin. Two reasons:
 *
 * 1. @Transactional on recalculateAll() only works because the call crosses a bean boundary
 *    and therefore goes through the proxy. If the @Scheduled method and the transactional
 *    work were the same method on the same class, the transaction would still apply -- but
 *    the moment anyone extracted a helper and called it internally, it would silently stop.
 *    Keeping the trigger and the work in separate beans removes the trap.
 *
 * 2. The cron and the manual admin endpoint both call the same service method, so "run it by
 *    hand to check" tests the real code path rather than something that resembles it.
 */
@Component
public class ProgressRecalculationJob {

    private static final Logger log = LoggerFactory.getLogger(ProgressRecalculationJob.class);

    private final ProgressRecalculationService service;
    private final JobProperties jobProperties;

    public ProgressRecalculationJob(ProgressRecalculationService service, JobProperties jobProperties) {
        this.service = service;
        this.jobProperties = jobProperties;
    }

    /*
     * Both cron and zone come from properties rather than being hardcoded, so dev can run it
     * every 20 seconds and prod at 2 AM without a profile-specific class.
     *
     * fixedRate vs fixedDelay vs cron, since the slides list them without saying when to use
     * which:
     *   fixedRate  -- every N ms from each START. Runs can overlap or stack up if the work
     *                 takes longer than N. Right for cheap polling, wrong for real work.
     *   fixedDelay -- N ms after each run FINISHES. Never overlaps with itself. This is the
     *                 sane default for anything that does actual work.
     *   cron       -- wall-clock times. The only one that can express "2 AM", which is what
     *                 a nightly job means. That is why it is used here.
     */
    @Scheduled(
            cron = "${competencyhub.jobs.progress-recalculation.cron}",
            zone = "${competencyhub.jobs.progress-recalculation.zone}")
    public void run() {
        if (!jobProperties.progressRecalculation().enabled()) {
            log.debug("progress recalculation is disabled by configuration");
            return;
        }
        service.recalculateAll();
    }
}
