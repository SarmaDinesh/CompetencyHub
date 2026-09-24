package com.example.CompetencyHub.messaging;

import com.example.CompetencyHub.domain.enums.NotificationSource;
import com.example.CompetencyHub.domain.model.Notification;
import com.example.CompetencyHub.messaging.event.EnrollmentCreatedEvent;
import com.example.CompetencyHub.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes enrollment events and creates a notification for the student.
 *
 * <p>This is the payoff: enrolling no longer waits for notification work. The HTTP
 * response returns as soon as the seat is reserved; everything downstream happens on
 * its own schedule. If this consumer is down, messages queue in the topic and are
 * processed when it comes back — enrollment keeps working throughout.
 *
 * <p>{@code concurrency = "3"} starts three consumer threads, matching the topic's three
 * partitions. More threads than partitions would leave the extras idle, since a partition
 * is only ever assigned to one consumer in a group at a time.
 */
@Component
public class EnrollmentEventListener {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentEventListener.class);

    private final NotificationRepository notificationRepository;

    public EnrollmentEventListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @KafkaListener(topics = KafkaTopics.ENROLLMENT_CREATED, concurrency = "3")
    @Transactional
    public void onEnrollmentCreated(EnrollmentCreatedEvent event) {
        log.info("Received enrollment event {} for course {}",
                event.enrollmentId(), event.courseCode());

        // Kafka guarantees at-least-once delivery, not exactly-once. A consumer that
        // processes a batch and dies before committing its offset will see those
        // messages again on restart. Consumers must therefore be idempotent — which
        // here means checking whether this event was already handled.
        if (notificationRepository.existsByEventTypeAndSourceEventId(
                NotificationSource.ENROLLMENT_CREATED, event.enrollmentId())) {
            log.debug("Enrollment {} already notified, skipping duplicate", event.enrollmentId());
            return;
        }

        notificationRepository.save(new Notification(
                event.studentEmail(),
                "Enrolled in " + event.courseCode(),
                "You are now enrolled in " + event.courseTitle()
                        + " (" + event.courseCode() + ").",
                NotificationSource.ENROLLMENT_CREATED,
                event.enrollmentId()
        ));
    }
}