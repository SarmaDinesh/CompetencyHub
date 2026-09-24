package com.example.CompetencyHub.web.dto.response;

import com.example.CompetencyHub.domain.model.Notification;

import java.time.Instant;

/**
 * What a client sees of a notification. sourceEventId and eventType stay internal: they
 * exist so the Kafka consumers can deduplicate, and are meaningless to a reader.
 */
public record NotificationResponse(Long id, String recipient, String subject, String body, Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getRecipient(), n.getSubject(), n.getBody(), n.getCreatedAt());
    }
}
