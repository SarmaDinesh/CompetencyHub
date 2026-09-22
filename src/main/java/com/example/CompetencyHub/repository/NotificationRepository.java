package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsBySourceEventId(Long sourceEventId);

    List<Notification> findByRecipientOrderByCreatedAtDesc(String recipient);
}
