package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.enums.NotificationSource;
import com.example.CompetencyHub.domain.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * The idempotency check for Kafka consumers. Was existsBySourceEventId, which could not
     * tell enrollment 5 from submission 5 -- see V9.
     */
    boolean existsByEventTypeAndSourceEventId(NotificationSource eventType, Long sourceEventId);

    List<Notification> findByRecipientOrderByCreatedAtDesc(String recipient);
}
