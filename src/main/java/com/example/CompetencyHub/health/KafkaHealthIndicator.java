package com.example.CompetencyHub.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Bean name determines the key in the JSON: "kafka" appears under health.components.kafka.
 * Spring strips the "HealthIndicator" suffix automatically.
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        // A health check MUST have a timeout. Without one, a broker that accepts the
        // connection but never responds makes /actuator/health hang -- and then the load
        // balancer's own probe times out, marks the instance dead, and you have an outage
        // caused by the thing meant to detect outages. Fail fast and report DOWN.
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult cluster = client.describeCluster();
            int nodeCount = cluster.nodes().get(3, TimeUnit.SECONDS).size();
            String clusterId = cluster.clusterId().get(3, TimeUnit.SECONDS);

            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodes", nodeCount)
                    .build();

        } catch (Exception ex) {
            // Message only, not the stack trace. /actuator/health may be reachable by
            // anything that can hit the port, and stack traces leak internal structure.
            Thread.currentThread().interrupt();   // restore the flag if it was InterruptedException
            return Health.down()
                    .withDetail("error", ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .build();
        }
    }
}
