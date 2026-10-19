# JobTrackr backend image: build with the JDK, run on a slim JRE as a non-root user.
# Build from this folder:  docker build -t jobtrackr-backend .
# (docker-compose.yml at the monorepo root builds it for `docker compose --profile full up --build`)

# ---------- 1. Build: JDK 21 + the Maven wrapper ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Dependencies first, in their own layer: editing a .java file does not re-download the world
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src/ src/
# Tests run in CI (mvnw verify with Testcontainers), not here: an image build has no Docker for them
RUN ./mvnw -q -B -DskipTests package \
 && cp target/jobtrackr-backend-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- 2. Run: JRE 21 only ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# A dedicated unprivileged user: a compromised app cannot modify the image or act as root in the container
RUN addgroup -S jobtrackr && adduser -S -G jobtrackr jobtrackr

# Spring Boot's layers, least to most frequently changed: a code-only change rebuilds just the last layer
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER jobtrackr

# MaxRAMPercentage sizes the heap from the container's memory limit (75% of 512 MB is about 384 MB),
# instead of from the host's RAM. ExitOnOutOfMemoryError lets the platform restart a broken JVM rather
# than leaving it half-alive. Overridable at run time with JAVA_TOOL_OPTIONS.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# server.port reads PORT (application.yml); 8080 when unset
ENV PORT=8080
EXPOSE 8080

# Public, detail-free health endpoint (SecurityConfig); busybox wget is part of the Alpine base image.
# start-period: Flyway and Hibernate need a few seconds before the first check counts.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -q -O /dev/null "http://localhost:${PORT}/actuator/health" || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
