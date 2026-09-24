package com.example.CompetencyHub.domain.model;

import com.example.CompetencyHub.domain.enums.NotificationSource;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Which kind of event this came from. Needed since V9: the id below is only unique within
     * one event type, so (eventType, sourceEventId) is the real identity of the event, and the
     * unique constraint that makes consumers idempotent is on the pair.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private NotificationSource eventType;

    /** Id of the enrollment or submission that caused this. */
    @Column(name = "source_event_id", nullable = false)
    private Long sourceEventId;

    protected Notification() {
    }

    public Notification(String recipient, String subject, String body,
                        NotificationSource eventType, Long sourceEventId) {
        this.recipient = recipient;
        this.eventType = eventType;
        this.subject = subject;
        this.body = body;
        this.sourceEventId = sourceEventId;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public NotificationSource getEventType() { return eventType; }
    public Long getSourceEventId() { return sourceEventId; }
}
