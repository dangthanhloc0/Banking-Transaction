
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

COPY pom.xml ./

RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd -r spring && useradd -r -g spring spring

COPY --from=builder /build/target/demo-0.0.1-SNAPSHOT.jar app.jar

RUN mkdir -p logs && chown -R spring:spring /app

USER spring

EXPOSE 1000:1000


ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-cp", "app.jar", \
  "com.ldang03.core.MainApplication"]
