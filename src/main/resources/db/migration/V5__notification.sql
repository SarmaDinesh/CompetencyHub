-- Notifications produced by consuming enrollment events.
CREATE TABLE notification (
                              id           BIGSERIAL PRIMARY KEY,
                              recipient    VARCHAR(255) NOT NULL,
                              subject      VARCHAR(200) NOT NULL,
                              body         TEXT NOT NULL,
                              created_at   TIMESTAMP NOT NULL,
    -- Source event id. Kafka delivers at-least-once, so the same message can arrive
    -- twice after a rebalance or a retry. A unique constraint makes the consumer
    -- idempotent: a duplicate delivery cannot create a duplicate notification.
                              source_event_id BIGINT NOT NULL UNIQUE
);

CREATE INDEX idx_notification_recipient ON notification(recipient);