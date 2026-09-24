package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.model.Notification;

import java.util.List;

public interface NotificationService {
    List<Notification> findByRecipient(String recipient);
}
