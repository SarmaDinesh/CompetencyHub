package com.example.CompetencyHub.web;

import com.example.CompetencyHub.service.NotificationService;
import com.example.CompetencyHub.web.dto.response.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Was the one controller that returned an entity and called a repository directly -- both
 * breaking the "entities never leave the service layer" rule. It went unnoticed because the
 * JSON looked fine. Generated API docs make it visible: the spec would have published the
 * table's internal columns (sourceEventId, eventType) as part of the public contract.
 */
@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "List notifications for a recipient",
            description = "Created asynchronously by the Kafka consumers after enrollment and grading.")
    @ApiResponse(responseCode = "200", description = "Newest first")
    @ApiResponse(responseCode = "400", description = "recipient parameter missing")
    @PreAuthorize("hasRole('ADMIN') or @access.isEmail(#recipient)")
    @GetMapping
    public List<NotificationResponse> byRecipient(
            @Parameter(description = "Email address the notifications were sent to", example = "student1@example.com")
            @RequestParam String recipient) {
        return notificationService.findByRecipient(recipient).stream()
                .map(NotificationResponse::from)
                .toList();
    }
}
