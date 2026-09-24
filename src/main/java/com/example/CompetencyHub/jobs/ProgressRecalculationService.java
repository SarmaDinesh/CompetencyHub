package com.example.CompetencyHub.jobs;

import com.example.CompetencyHub.config.JobProperties;
import com.example.CompetencyHub.domain.enums.EnrollmentStatus;
import com.example.CompetencyHub.domain.model.Enrollment;
import com.example.CompetencyHub.domain.model.JobRun;
import com.example.CompetencyHub.domain.model.Progress;
import com.example.CompetencyHub.domain.model.ProgressId;
import com.example.CompetencyHub.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class ProgressRecalculationService {

    private static final Logger log = LoggerFactory.getLogger(ProgressRecalculationService.class);
    public static final String JOB_NAME = "progress-recalculation";

    private final EnrollmentRepository enrollmentRepository;
    private final CompetencyRepository competencyRepository;
    private final ProgressRepository progressRepository;
    private final JobRunRepository jobRunRepository;
    private final JobLockRepository jobLockRepository;
    private final SubmissionRepository submissionRepository;
    private final JobProperties jobProperties;
    private final ApplicationEventPublisher eventPublisher;

    public ProgressRecalculationService(EnrollmentRepository enrollmentRepository,
                                        CompetencyRepository competencyRepository,
                                        ProgressRepository progressRepository,
                                        JobRunRepository jobRunRepository,
                                        JobLockRepository jobLockRepository, SubmissionRepository submissionRepository,
                                        JobProperties jobProperties,
                                        ApplicationEventPublisher eventPublisher) {
        this.enrollmentRepository = enrollmentRepository;
        this.competencyRepository = competencyRepository;
        this.progressRepository = progressRepository;
        this.jobRunRepository = jobRunRepository;
        this.jobLockRepository = jobLockRepository;
        this.submissionRepository = submissionRepository;
        this.jobProperties = jobProperties;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @Transactional for the whole run, which is what makes the advisory lock hold for the
     * whole run. Honest about the trade-off: one long transaction keeps a connection checked
     * out and holds back vacuum on the rows it touches. Fine at this size, wrong at a million
     * enrollments -- see "Known gaps" in the PR for what replaces it.
     */
    @Transactional
    public JobResult recalculateAll() {
        Instant start = Instant.now();

        // Take the lock FIRST, before writing anything. A second instance that loses the race
        // records a SKIPPED run and returns, so the audit table shows the contention happened
        // rather than hiding it.
        if (!jobLockRepository.tryLock(jobProperties.lockKey())) {
            log.info("{} skipped: another instance holds the lock", JOB_NAME);
            JobRun skipped = new JobRun(JOB_NAME);
            skipped.skipped("another instance holds the advisory lock");
            jobRunRepository.save(skipped);
            return JobResult.skipped();
        }

        JobRun run = jobRunRepository.save(new JobRun(JOB_NAME));
        int processed = 0;
        int updated = 0;

        try {
            // Cached per course. Without this, a page of 200 enrollments across 5 courses
            // issues 200 count queries instead of 5 -- the N+1 shape from Module 5, wearing
            // a different hat. Scheduled jobs are where this hurts least visibly and costs
            // most, because nobody is watching a response time at 2 AM.
            Map<Long, Long> competencyCountByCourse = new HashMap<>();

            int pageNumber = 0;
            Page<Enrollment> page;

            do {
                // Sorted by id so paging is stable. Paging an unsorted query lets Postgres
                // return rows in any order it likes, which means page 2 can repeat or skip
                // rows from page 1. A real and very confusing bug.
                var pageable = PageRequest.of(pageNumber, jobProperties.progressRecalculation().batchSize(),
                        Sort.by("id"));
                page = enrollmentRepository.findByStatus(EnrollmentStatus.ACTIVE, pageable);

                for (Enrollment enrollment : page.getContent()) {
                    processed++;

                    Long courseId = enrollment.getCourse().getId();
                    Long studentId = enrollment.getStudent().getId();

                    long total = competencyCountByCourse.computeIfAbsent(
                            courseId, competencyRepository::countByCourseId);

                    if (upsertProgress(studentId, courseId, total)) {
                        updated++;
                    }
                }

                checkRuntime(start);
                pageNumber++;
            } while (page.hasNext());

            run.succeeded(processed);
            log.info("{} finished: {} enrollments examined, {} updated, took {}",
                    JOB_NAME, processed, updated, Duration.between(start, Instant.now()));

            // In-process event. Fired inside the transaction, so a listener marked
            // @TransactionalEventListener(AFTER_COMMIT) sees it only if the job commits --
            // the same guarantee we relied on for the Kafka publisher in Module 11.
            eventPublisher.publishEvent(new ProgressRecalculatedEvent(processed, updated, start));

            return JobResult.completed(processed, updated);

        } catch (RuntimeException ex) {
            // The JobRun row is part of this transaction, so marking it FAILED here and then
            // rethrowing rolls the mark back along with everything else. Recording a failure
            // durably needs a separate transaction -- REQUIRES_NEW, from Module 6. Noted as a
            // gap rather than quietly half-done.
            run.failed(ex.toString());
            log.error("{} failed after {} enrollments", JOB_NAME, processed, ex);
            throw ex;
        }
    }

    /**
     * Recalculates one student's progress in one course, right now. Called by the Kafka
     * consumer after a grade, so progress moves within seconds instead of waiting for 2 AM.
     *
     * <p>The nightly job stays: it is the safety net that repairs anything a lost message or a
     * failed consumer left behind. Event-driven for speed, scheduled batch for correctness --
     * a common pairing.
     *
     * <p>No advisory lock here. The lock protects the nightly job from a second copy of
     * ITSELF; this touches a single row, and the Kafka key (student id) already stops two
     * consumer threads updating the same student at once.
     *
     * @return true if the stored numbers changed
     */
    @Transactional
    public boolean recalculateFor(Long studentId, Long courseId) {
        return upsertProgress(studentId, courseId, competencyRepository.countByCourseId(courseId));
    }

    /**
     * Shared by the batch loop and the single-student path, so there is exactly one
     * definition of "how progress is computed". Two copies would drift apart.
     *
     * <p>Upsert: the composite key means findById is the existence check, and a missing row is
     * a student who has never been recalculated -- normal right after enrollment, not an error.
     */
    private boolean upsertProgress(Long studentId, Long courseId, long totalCompetencies) {
        long mastered = submissionRepository.countMasteredCompetencies(studentId, courseId);

        Progress progress = progressRepository
                .findById(new ProgressId(studentId, courseId))
                .orElseGet(() -> progressRepository.save(new Progress(studentId, courseId)));

        return progress.recalculate(mastered, totalCompetencies);
    }

    private void checkRuntime(Instant start) {
        Duration elapsed = Duration.between(start, Instant.now());
        if (elapsed.compareTo(jobProperties.progressRecalculation().maxRuntime()) > 0) {
            // Warn rather than abort. Aborting would roll back everything computed so far,
            // which is worse than a slow job. This is the signal that the batch approach has
            // outgrown itself.
            log.warn("{} has been running {} which exceeds configured maxRuntime {}",
                    JOB_NAME, elapsed, jobProperties.progressRecalculation().maxRuntime());
        }
    }

    public record JobResult(boolean ran, int examined, int updated) {
        static JobResult skipped()                      { return new JobResult(false, 0, 0); }
        static JobResult completed(int e, int u)        { return new JobResult(true, e, u); }
    }
}
