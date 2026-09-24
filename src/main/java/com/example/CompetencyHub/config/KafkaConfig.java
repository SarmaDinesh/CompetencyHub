package com.example.CompetencyHub.config;

import com.example.CompetencyHub.messaging.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    /**
     * Creates the topic at startup if it does not exist.
     *
     * <p>Kafka auto-creates topics by default, but with the broker's defaults — usually
     * one partition. Declaring it explicitly makes partition count a deliberate choice.
     *
     * <p><b>Partitions are the unit of parallelism.</b> Three partitions means at most
     * three consumers in a group can work simultaneously; a fourth sits idle. Ordering is
     * guaranteed within a partition, not across the topic — so messages that must stay in
     * order need the same key, which is why the publisher keys by course id.
     *
     * <p>Partition count can be increased later but never decreased, so it is worth a
     * moment's thought now.
     */
    @Bean
    public NewTopic enrollmentCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.ENROLLMENT_CREATED)
                .partitions(3)
                .replicas(1)        // single-broker dev cluster; 3 in production
                .build();
    }

    /** Same shape as the enrollment topic. Keyed by student id -- see SubmissionEventPublisher. */
    @Bean
    public NewTopic submissionGradedTopic() {
        return TopicBuilder.name(KafkaTopics.SUBMISSION_GRADED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
