# syntax=docker/dockerfile:1
#
# Two stages: a JDK image that BUILDS the jar, and a JRE image that RUNS it.
# Only the runtime stage becomes the final image.

# =============================================================================
# Stage 1: build
# =============================================================================
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# --- 1. Build definition first: changes rarely, so its layers stay cached ------
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Belt and braces: Git now stores mvnw as executable, but a checkout on Windows
# can still lose the bit. Cheap to guarantee.
RUN chmod +x mvnw

# Download dependencies. The cache mount keeps ~/.m2 between builds on this
# machine WITHOUT putting it in an image layer.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

# --- 2. Source code: changes constantly, so it comes after the slow step -----
COPY src src

# Tests are skipped here on purpose: CI runs the full test suite (including the
# Testcontainers ITs, which need Docker -- awkward inside a Docker build).
# The image build only has to produce the jar.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

# --- 3. Split the fat jar into layers ----------------------------------------
# Spring Boot's jarmode "tools" extracts four folders:
#   dependencies/           third-party libraries        (changes rarely)
#   spring-boot-loader/     Boot's launcher classes      (changes rarely)
#   snapshot-dependencies/  -SNAPSHOT libraries          (changes sometimes)
#   application/            YOUR classes and resources   (changes every commit)
RUN cp target/*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# =============================================================================
# Stage 2: runtime
# =============================================================================
# JRE, not JDK: no compiler, no Maven, no source code -- smaller image, smaller
# attack surface.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# A system user with no login shell and no home directory. The app has no
# reason to run as root.
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring

# Least-changing layer first, so a code change rebuilds and uploads only the
# last (small) layer.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER spring
EXPOSE 8080

# Container-aware memory: the heap may use 75% of the container's memory limit
# (the JVM default is 25%). Exit on OutOfMemoryError so the orchestrator
# restarts a clean process instead of leaving a half-broken one running.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# Exec form (JSON array): java becomes PID 1 and receives SIGTERM directly,
# so `docker stop` triggers Spring's graceful shutdown instead of a kill.
ENTRYPOINT ["java", "-jar", "application.jar"]