package com.example.CompetencyHub.messaging;

import com.example.CompetencyHub.messaging.event.EnrollmentCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends enrollment events to Kafka <b>after</b> the database transaction commits.
 *
 * <p><b>The dual-write problem.</b> The obvious implementation calls
 * {@code kafkaTemplate.send(...)} inside {@code EnrollmentService.enroll()}. That is
 * wrong, and the failure is nasty: Kafka has no part in your database transaction, so if
 * the send succeeds and the transaction then rolls back, you have told the world a
 * student enrolled when no enrollment exists. Consumers send emails for nothing. The
 * inconsistency is permanent — there is no message to un-send.
 *
 * <p>{@code @TransactionalEventListener} with {@code AFTER_COMMIT} fixes the common case:
 * the service publishes an in-process Spring event, this listener runs only once the
 * transaction has committed, and only then does anything reach Kafka.
 *
 * <p>It is not a complete fix. If the application crashes between commit and send, the
 * enrollment exists and no event was published. The full solution is the <b>transactional
 * outbox</b>: write the event to a table in the same transaction, and have a separate
 * process relay it to Kafka.
 */
@Component
public class EnrollmentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EnrollmentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(EnrollmentCreatedEvent event) {
        // The key decides the partition. Keying by course id means all events for one
        // course land in the same partition and are therefore consumed in order.
        // A null key would round-robin them and lose that ordering.
        String key = String.valueOf(event.courseId());

        kafkaTemplate.send(KafkaTopics.ENROLLMENT_CREATED, key, event)
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        // Logged, not rethrown: the enrollment is already committed and
                        // is not in doubt. Failing the HTTP response now would be a lie.
                        log.error("Failed to publish enrollment event {}", event.enrollmentId(), failure);
                    } else {
                        log.info("Published enrollment {} to partition {}",
                                event.enrollmentId(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}