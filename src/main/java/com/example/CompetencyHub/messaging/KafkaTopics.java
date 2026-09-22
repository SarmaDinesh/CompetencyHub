package com.example.CompetencyHub.messaging;

/** Topic names in one place — a typo in a string literal creates a silent new topic. */
public final class KafkaTopics {

    public static final String ENROLLMENT_CREATED = "competencyhub.enrollment.created";

    private KafkaTopics() {
    }
}
