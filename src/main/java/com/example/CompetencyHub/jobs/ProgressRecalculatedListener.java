package com.example.CompetencyHub.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ProgressRecalculatedListener {

    private static final Logger log = LoggerFactory.getLogger(ProgressRecalculatedListener.class);

    /**
     * @Async + @TransactionalEventListener together. Each does one thing:
     *   - AFTER_COMMIT: do not react to work that ended up rolled back.
     *   - @Async: do not make the job's transaction wait for this. A plain @EventListener runs
     *     on the publisher's thread, so slow listener == slow job, which is rarely what anyone
     *     intends when they reach for events.
     *
     * The cost of @Async on a void method: any exception thrown here goes to the
     * AsyncUncaughtExceptionHandler, which by default logs and drops it. Nothing propagates to
     * the job. So @Async void is for work whose failure is genuinely tolerable. Returning
     * CompletableFuture instead lets the caller see the failure -- which is what the admin
     * endpoint below does.
     */
    @Async("jobExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProgressRecalculated(ProgressRecalculatedEvent event) {
        log.info("progress recalculation committed: {} examined, {} updated, started at {}",
                event.examined(), event.updated(), event.startedAt());
    }
}
