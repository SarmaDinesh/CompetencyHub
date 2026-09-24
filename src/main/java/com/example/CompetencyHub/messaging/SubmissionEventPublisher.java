package com.example.CompetencyHub.messaging;

import com.example.CompetencyHub.messaging.event.SubmissionGradedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends grading events to Kafka after the grade commits. Same after-commit pattern, and the
 * same known gap (a crash between commit and send loses the event), as
 * EnrollmentEventPublisher -- see its comment on the transactional outbox.
 */
@Component
public class SubmissionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SubmissionEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SubmissionEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(SubmissionGradedEvent event) {
        /*
         * Keyed by STUDENT, not course -- a different choice from enrollments, for a reason.
         *
         * The consumer recalculates that student's progress. If two grades for the same
         * student landed on different partitions, two consumer threads could recalculate the
         * same progress row at the same moment. The same key means the same partition, which
         * means one thread, in order. The key is chosen by asking "what must never be
         * processed concurrently?" -- here, one student's progress.
         */
        String key = String.valueOf(event.studentId());

        kafkaTemplate.send(KafkaTopics.SUBMISSION_GRADED, key, event)
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        log.error("Failed to publish grade for submission {}", event.submissionId(), failure);
                    } else {
                        log.info("Published grade for submission {} to partition {}",
                                event.submissionId(), result.getRecordMetadata().partition());
                    }
                });
    }
}
