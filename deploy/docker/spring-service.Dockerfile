# syntax=docker/dockerfile:1
# =============================================================================
# AquaOps: shared image for every Spring Boot service (Java 21).
# Card [TECH] deployment · E5
#
# The build context is the service folder, the Dockerfile stays here:
#     docker build -f deploy/docker/spring-service.Dockerfile \
#                  -t aquaops/simulator:dev services/simulator
#
# Nothing environment-specific is baked in: port, database, Kafka and URLs
# come from the ${VARIABLE:default} settings of each service, at run time.
#
# Health check: give the port the service listens on, at run time:
#     docker run -e HEALTHCHECK_PORT=8086 aquaops/simulator:dev
# (gateway 8080, telemetry 8081, assets 8082, detection 8083,
#  incidents 8084, notifications 8085, simulator 8086)
# =============================================================================

# ---- Stage 1: build the jar with the service's own Maven wrapper -----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Only what the build needs is copied, file by file: never .env, .git,
# target/ or IDE files, even if they exist in the service folder.
# Wrapper and pom first: the dependency layer is reused until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn

# "sh ./mvnw": mvnw is not executable in Git (mode 644).
# The cache mount keeps ~/.m2 between builds (BuildKit), outside the image.
# Downloads only, retried up to 3 times: the Maven wrapper download or Maven
# Central sometimes fails on GitHub runners (Images run of 19/11/2026: this
# step failed in 10 s for detection). The build below runs once.
RUN --mount=type=cache,target=/root/.m2 \
    for attempt in 1 2 3; do \
      sh ./mvnw -B -q dependency:go-offline && exit 0; \
      echo "download attempt $attempt failed (network), retrying in 20 s"; sleep 20; \
    done; \
    echo "Maven or the dependencies could not be downloaded 3 times (network)"; exit 1

COPY src src

# Tests need PostgreSQL or Kafka, absent during an image build:
# they run outside, with ./mvnw test. The build fails unless Maven
# produced exactly one runnable jar.
RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw -B -q package -DskipTests \
 && set -- target/*.jar \
 && if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then \
        echo "Expected exactly one jar in target/, found: $*" >&2; exit 1; \
    fi \
 && cp "$1" /app.jar

# ---- Stage 2: runtime, JRE only, non-root user -----------------------------
FROM eclipse-temurin:21-jre

# The HEALTHCHECK below uses curl, shipped by the eclipse-temurin image
# (Ubuntu). The tag moves: fail the build now rather than ship an image
# that can never become healthy.
RUN if ! command -v curl > /dev/null; then \
        echo "curl not found in the runtime image" >&2; exit 1; \
    fi \
 && groupadd --system aquaops \
 && useradd --system --gid aquaops --no-create-home aquaops

WORKDIR /app

# OpenTelemetry Java agent (SCRUM-58, traces). Present in the image but NOT
# attached by default: a deployment turns tracing on by adding
#   JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -javaagent:/otel/opentelemetry-javaagent.jar"
# plus OTEL_SERVICE_NAME and OTEL_EXPORTER_OTLP_ENDPOINT. Version pinned, SHA-256 checked.
ADD --checksum=sha256:e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149 --chmod=755 \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.33.1/opentelemetry-javaagent.jar \
    /otel/opentelemetry-javaagent.jar
COPY --from=build --chown=aquaops:aquaops /app.jar app.jar
USER aquaops

# The heap follows the container memory limit instead of the host RAM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Healthy = /actuator/health answers 200 (UP). DOWN answers 503: curl -f fails.
# HEALTHCHECK_PORT is read at run time, so one image works whatever port the
# service is given. Missing or not a number: unhealthy, with the reason in
# "docker inspect". 127.0.0.1 is the container itself, not a host address.
# start-period: JVM and Spring startup; failures in it do not count.
# A healthcheck: block in docker compose replaces this one.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
    CMD case "${HEALTHCHECK_PORT:-}" in \
            ""|*[!0-9]*) echo "HEALTHCHECK_PORT must be the service port, got [${HEALTHCHECK_PORT:-}]" >&2; exit 1 ;; \
        esac; \
        curl -fsS --max-time 4 -o /dev/null "http://127.0.0.1:${HEALTHCHECK_PORT}/actuator/health" || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
