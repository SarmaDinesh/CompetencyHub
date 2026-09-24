package com.example.CompetencyHub;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    /**
     * @ServiceConnection replaces the @DynamicPropertySource dance: Boot reads the running
     * container's JDBC URL, username and password and points the DataSource at them.
     *
     * Postgres 16 matches docker-compose, on purpose. Testing against a different major
     * version than you deploy to reintroduces the exact problem Testcontainers exists to
     * solve -- it is just a smaller gap than H2's.
     *
     * The container is started once and reused across every test class in the run, because
     * Spring caches the application context between classes that share configuration.
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}