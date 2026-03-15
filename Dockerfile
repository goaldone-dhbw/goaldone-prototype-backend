# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy maven wrapper and pom.xml first for layer caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (go-offline)
RUN ./mvnw dependency:go-offline -B

# Copy source and build the application
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Create a non-root user
RUN addgroup -S goaldone && adduser -S goaldone -G goaldone
USER goaldone

WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose app and actuator ports
EXPOSE 8080 8081

# Optimize JVM for container environments
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", \
            "app.jar"]
