# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace/app

# Copy the pom.xml and source code
COPY backend/pom.xml ./
COPY backend/src ./src

# Build the application (skip tests for faster builds)
# Override the java.version property from pom.xml to use Java 21
RUN mvn package -DskipTests -Djava.version=21

# Runtime stage
FROM eclipse-temurin:21-jre

# Create app directory
WORKDIR /app

# Copy the jar from the build stage
COPY --from=build /workspace/app/target/*.jar /app/app.jar

# Set JAVA_OPTS environment variable for JVM tuning
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Expose the default port
EXPOSE 8080

# Run the application
# The --server.port flag allows Cloud Run to set the port via the PORT environment variable
# If PORT is not set, it defaults to 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --server.port=${PORT:-8080}"]
