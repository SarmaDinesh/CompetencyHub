package com.example.CompetencyHub.messaging;

import com.example.CompetencyHub.domain.enums.NotificationSource;
import com.example.CompetencyHub.domain.model.Notification;
import com.example.CompetencyHub.jobs.ProgressRecalculationService;
import com.example.CompetencyHub.messaging.event.SubmissionGradedEvent;
import com.example.CompetencyHub.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The post-grading work, done off the request path.
 *
 * <p>A mentor's "grade" request returns as soon as the grade is saved. Telling the student
 * and updating their progress happen here, afterwards, on a consumer thread. If this consumer
 * is down, grading still works and the messages wait in the topic.
 *
 * <p><b>Two kinds of idempotency</b> -- worth telling apart, because Kafka will redeliver:
 * <ul>
 *   <li>The notification is idempotent <i>by constraint</i>: inserting it twice would create
 *       a duplicate, so we check first, and UNIQUE (event_type, source_event_id) backs it up.</li>
 *   <li>The progress update is idempotent <i>by nature</i>: it recomputes from the
 *       submissions table every time, so running it twice gives the same answer. No check
 *       needed. "Recompute from the source" is the easiest way to make a consumer safe.</li>
 * </ul>
 */
@Component
public class SubmissionGradedListener {

    private static final Logger log = LoggerFactory.getLogger(SubmissionGradedListener.class);

    private final NotificationRepository notificationRepository;
    private final ProgressRecalculationService progressService;

    public SubmissionGradedListener(NotificationRepository notificationRepository,
                                    ProgressRecalculationService progressService) {
        this.notificationRepository = notificationRepository;
        this.progressService = progressService;
    }

    /*
     * One transaction for both steps. If the progress update fails, the notification rolls
     * back too, the offset is not committed, and Kafka redelivers the whole message -- so the
     * student is never told about a grade their progress does not yet reflect.
     */
    @KafkaListener(topics = KafkaTopics.SUBMISSION_GRADED, concurrency = "3")
    @Transactional
    public void onSubmissionGraded(SubmissionGradedEvent event) {
        log.info("Received grade for submission {} (student {}, course {})",
                event.submissionId(), event.studentId(), event.courseCode());

        if (!notificationRepository.existsByEventTypeAndSourceEventId(
                NotificationSource.SUBMISSION_GRADED, event.submissionId())) {
            notificationRepository.save(new Notification(
                    event.studentEmail(),
                    "Graded: " + event.assessmentTitle(),
                    "You scored " + event.score() + " on " + event.assessmentTitle()
                            + " in " + event.courseCode() + ". "
                            + (event.passed() ? "Passed -- competency mastered." : "Not yet passing; you can try again."),
                    NotificationSource.SUBMISSION_GRADED,
                    event.submissionId()));
        } else {
            log.debug("Submission {} already notified, skipping duplicate", event.submissionId());
        }

        // Joins this transaction (REQUIRED propagation): same connection, same commit.
        progressService.recalculateFor(event.studentId(), event.courseId());
    }
}
