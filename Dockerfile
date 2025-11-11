# Multi-stage build for Spring Boot application
# Build stage - using latest Maven with Eclipse Temurin 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy pom.xml first for better layer caching
COPY backend/pom.xml .

# Download dependencies (this step will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B || true

# Copy source code
COPY backend/src src/
COPY backend/.mvn .mvn/
COPY backend/mvnw .
COPY backend/mvnw.cmd .

# Build the application, skipping tests
RUN mvn -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Copy the built jar from build stage
COPY --from=build /build/target/*.jar /app/app.jar

# Expose port 8080
EXPOSE 8080

# Run the application
# PORT environment variable can be set by Cloud Run
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --server.port=${PORT:-8080}"]
