# CompetencyHub

Competency-based learning platform API: students enroll in courses, courses are made of
competencies, competencies are proven by objective tests or mentor-graded performance tasks,
and progress recalculates as grades arrive.

**Stack:** Java 21 · Spring Boot 4.1 · Spring Data JPA · PostgreSQL + Flyway · Kafka ·
Actuator / Micrometer / Prometheus · springdoc-openapi · JUnit 5 · Mockito · Rest Assured ·
Testcontainers

## Run it

```bash
docker compose up -d          # Postgres, Kafka, Prometheus, Grafana
./mvnw spring-boot:run        # dev profile: seeds sample courses, students and a mentor
```

| What | URL |
|---|---|
| API docs (Swagger UI) | http://localhost:8080/swagger-ui.html |
| OpenAPI spec | http://localhost:8080/v3/api-docs (`.yaml` for YAML) |
| Health | http://localhost:8080/actuator/health |
| Grafana | http://localhost:3000 |

A Postman collection covering every endpoint is in `docs/postman/`. Postman can also import
the live spec directly: *Import → Link → `http://localhost:8080/v3/api-docs`*.

## Test it

```bash
./mvnw test      # unit + web-slice tests (SeatLimitConcurrencyTest also needs the compose Postgres)
./mvnw verify    # + integration tests against Postgres in Testcontainers (Docker required)
```
