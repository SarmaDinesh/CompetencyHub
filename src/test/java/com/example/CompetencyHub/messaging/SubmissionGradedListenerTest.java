package com.example.CompetencyHub.messaging;

import com.example.CompetencyHub.domain.enums.NotificationSource;
import com.example.CompetencyHub.domain.model.Notification;
import com.example.CompetencyHub.jobs.ProgressRecalculationService;
import com.example.CompetencyHub.messaging.event.SubmissionGradedEvent;
import com.example.CompetencyHub.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The consumer's logic without Kafka. Calling the @KafkaListener method directly is exactly
 * what the listener container does after deserializing -- so this tests everything except
 * the transport, which is Spring's code, not ours.
 */
@ExtendWith(MockitoExtension.class)
class SubmissionGradedListenerTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private ProgressRecalculationService progressService;
    @InjectMocks private SubmissionGradedListener listener;

    private final SubmissionGradedEvent event = new SubmissionGradedEvent(
            5L, 1L, "ada@example.com", 200L, "Essay", 10L, "CS544",
            75, true, false, Instant.now());

    @Test
    void firstDeliveryNotifiesTheStudentAndUpdatesProgress() {
        when(notificationRepository.existsByEventTypeAndSourceEventId(NotificationSource.SUBMISSION_GRADED, 5L))
                .thenReturn(false);

        listener.onSubmissionGraded(event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getRecipient()).isEqualTo("ada@example.com");
        assertThat(saved.getValue().getEventType()).isEqualTo(NotificationSource.SUBMISSION_GRADED);
        assertThat(saved.getValue().getBody()).contains("75").contains("Passed");

        verify(progressService).recalculateFor(1L, 10L);
    }

    @Test
    void aRedeliveredMessageDoesNotNotifyTwiceButStillRecalculates() {
        when(notificationRepository.existsByEventTypeAndSourceEventId(NotificationSource.SUBMISSION_GRADED, 5L))
                .thenReturn(true);

        listener.onSubmissionGraded(event);

        // Idempotent by constraint: skipped.
        verify(notificationRepository, never()).save(any());
        // Idempotent by nature: safe to run again, so it simply runs.
        verify(progressService).recalculateFor(1L, 10L);
    }
}
