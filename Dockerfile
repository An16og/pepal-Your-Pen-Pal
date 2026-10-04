# ==============================================================================
# Stage 1: Build the Spring Boot application using Eclipse Temurin JDK 17
# ==============================================================================
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /build

# Copy Maven wrapper files and pom.xml for dependency caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalize line endings for mvnw in case checked out on Windows with CRLF
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Download dependencies offline (cache layer)
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and build production fat JAR
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ==============================================================================
# Stage 2: Runtime image using Eclipse Temurin JRE 17
# ==============================================================================
FROM eclipse-temurin:17-jre-jammy AS runner

WORKDIR /app

# Install curl for container healthchecks and create non-root user
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd -r pepal && useradd -r -g pepal -d /app pepal

# Copy compiled executable JAR from builder stage
COPY --from=builder --chown=pepal:pepal /build/target/*.jar app.jar

USER pepal

EXPOSE 8080

ENV SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080 \
    JAVA_OPTS="-Xmx512m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
    CMD curl -f http://localhost:8080/ || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
