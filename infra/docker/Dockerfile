# One Dockerfile for all three services. Build context = repo root (CommonLib is a sibling module).
#   docker build -f infra/docker/Dockerfile --build-arg SERVICE=AccountService -t pm-account-service:local .
ARG SERVICE=AccountService

# ---------- Stage 1: build CommonLib, then the service ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
ARG SERVICE
WORKDIR /build
COPY CommonLib/pom.xml CommonLib/pom.xml
COPY CommonLib/src CommonLib/src
COPY ${SERVICE}/pom.xml ${SERVICE}/pom.xml
COPY ${SERVICE}/src ${SERVICE}/src
# BuildKit cache keeps the downloaded dependencies between builds
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -f CommonLib/pom.xml install -DskipTests \
 && mvn -B -q -f ${SERVICE}/pom.xml package -DskipTests \
 && cp ${SERVICE}/target/${SERVICE}-0.0.1-SNAPSHOT.jar /build/app.jar

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd -r spring && useradd -r -g spring spring \
 && mkdir -p /app/logs && chown -R spring:spring /app
COPY --from=builder --chown=spring:spring /build/app.jar app.jar
USER spring
# Heap is set per container with JAVA_TOOL_OPTIONS (see docker-compose.yml)
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
