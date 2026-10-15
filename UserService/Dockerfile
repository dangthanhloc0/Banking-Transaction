# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copy pom.xml trước để cache layer dependencies
COPY pom.xml ./

# Download tất cả dependencies (sẽ cache nếu pom.xml không đổi)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build shaded JAR, bỏ qua test
RUN mvn package -DskipTests -B

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Tạo user non-root để tăng bảo mật
RUN groupadd -r spring && useradd -r -g spring spring

# Copy JAR từ stage build
COPY --from=builder /build/target/demo-0.0.1-SNAPSHOT.jar app.jar

# Tạo thư mục logs
RUN mkdir -p logs && chown -R spring:spring /app

USER spring

EXPOSE 2807

# Dùng -cp thay vì -jar vì spring-boot-maven-plugin bị skip (dành cho Lambda)
# Shaded JAR chứa toàn bộ dependencies nên chạy được qua classpath
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-cp", "app.jar", \
  "com.ldang03.core.MainApplication"]
