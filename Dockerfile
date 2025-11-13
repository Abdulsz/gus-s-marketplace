# Multi-stage build for Spring Boot application
# Build stage
FROM maven:3.9.4-eclipse-temurin-21 AS build
WORKDIR /build

# Copy pom.xml first for better layer caching
COPY backend/pom.xml .
# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY backend/src ./src

# Build the application, skipping tests
RUN mvn clean package -DskipTests

# Runtime stage - use JRE instead of JDK for smaller image
FROM eclipse-temurin:21-jre
WORKDIR /app

# Create a non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring

# Copy the built jar from build stage and set ownership
COPY --from=build --chown=spring:spring /build/target/*.jar app.jar

# Switch to non-root user
USER spring:spring

# Expose port 8080
EXPOSE 8080

# Run the application
# PORT environment variable can be set by Cloud Run
# Using shell form to allow environment variable substitution
CMD java ${JAVA_OPTS} -jar app.jar --server.port=${PORT:-8080}
