# ==============================================================================
# Build Stage: Compile and Package Spring Boot Application
# ==============================================================================
FROM maven:3.9.8-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copy Maven POM and pre-fetch dependencies for Docker cache optimization
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy application source code
COPY src ./src

# Build production jar without running integration tests during container image build
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Runtime Stage: Lightweight JRE Container
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Run as non-root user for enhanced security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the built jar from the builder stage
COPY --from=builder /build/target/*.jar app.jar

# Container configuration
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
