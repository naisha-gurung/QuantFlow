# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper and pom first (layer cache optimization)
COPY pom.xml .
COPY .mvn/ .mvn/
# Download dependencies before copying source (better caching)
RUN apk add --no-cache maven && mvn dependency:go-offline -B

# Copy source and build
COPY src/ src/
RUN mvn clean package -DskipTests -B

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user (security best practice)
RUN addgroup -g 1001 -S quantflow && \
    adduser  -u 1001 -S quantflow -G quantflow

# Copy JAR from builder stage
COPY --from=builder /app/target/quantflow-*.jar app.jar

# Copy frontend static files
COPY --from=builder /app/target/classes/static/ ./static/ 2>/dev/null || true

# Set ownership
RUN chown -R quantflow:quantflow /app
USER quantflow

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep '"status":"UP"' || exit 1

EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.profiles.active=docker"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
