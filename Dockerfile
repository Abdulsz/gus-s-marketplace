# Multi-stage build for Spring Boot application
# Build stage - using latest Maven 3.9 with Eclipse Temurin 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy and install mkcert development certificate (for CI/CD environments with SSL interception)
COPY mkcert-ca.crt /usr/local/share/ca-certificates/
RUN update-ca-certificates && \
    # Import certificate into Java truststore for Maven SSL connections
    keytool -import -trustcacerts -keystore $JAVA_HOME/lib/security/cacerts \
            -storepass changeit -noprompt -alias mkcert-dev-ca -file /usr/local/share/ca-certificates/mkcert-ca.crt

# Copy pom.xml and source code
COPY backend/pom.xml .
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
CMD java $JAVA_OPTS -jar /app/app.jar --server.port=${PORT:-8080}
