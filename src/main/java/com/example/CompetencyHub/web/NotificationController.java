package com.example.CompetencyHub.web;

import com.example.CompetencyHub.domain.model.Notification;
import com.example.CompetencyHub.repository.NotificationRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public List<Notification> byRecipient(@RequestParam String recipient) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(recipient);
    }
}