package com.example.CompetencyHub.domain.model;

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

    /** Id of the enrollment that caused this. Unique, so replays cannot duplicate it. */
    @Column(name = "source_event_id", nullable = false, unique = true)
    private Long sourceEventId;

    protected Notification() {
    }

    public Notification(String recipient, String subject, String body, Long sourceEventId) {
        this.recipient = recipient;
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
    public Long getSourceEventId() { return sourceEventId; }
}
