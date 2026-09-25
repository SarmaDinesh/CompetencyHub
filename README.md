# CompetencyHub

Competency-based learning platform API: students enroll in courses, courses are made of
competencies, competencies are proven by objective tests or mentor-graded performance tasks,
and progress recalculates as grades arrive.

**Stack:** Java 21 · Spring Boot 4.1 · Spring Data JPA · PostgreSQL + Flyway · Kafka ·
Spring Security (OAuth2 resource server, RS256 JWT) ·
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

### Logging in

Every endpoint except register/login and the docs needs a bearer token.

```bash
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"password123"}'
# -> {"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":3600,...}
curl -s localhost:8080/api/courses -H "Authorization: Bearer eyJ..."
```

The dev profile seeds `admin@example.com`, `mentor@example.com` and `student1@example.com`,
all with password `password123`. Students sign up with `POST /api/auth/register`; mentors are
created by an admin. In Swagger UI: log in, click **Authorize**, paste the `accessToken`.

| Role | Can |
|---|---|
| ADMIN | manage courses, competencies, assessments, mentors; run jobs; read everything |
| MENTOR | see grading queues, grade as themselves |
| STUDENT | enroll themselves, submit their own work, read their own submissions and notifications |

A Postman collection covering every endpoint is in `docs/postman/` (run folder `0. Auth` first). Postman can also import
the live spec directly: *Import → Link → `http://localhost:8080/v3/api-docs`*.

## Test it

```bash
./mvnw test      # unit + web-slice tests (SeatLimitConcurrencyTest also needs the compose Postgres)
./mvnw verify    # + integration tests against Postgres in Testcontainers (Docker required)
```
