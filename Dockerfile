# ─────────────────────────────────────────────────────────────────────
# STAGE 1: BUILD
# Uses the full Maven + JDK image to compile the app and produce a JAR.
# This stage is NOT included in the final image — only its output is.
# ─────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml first and download dependencies.
# Docker caches this layer — if pom.xml hasn't changed, this step is
# skipped on subsequent builds, making rebuilds much faster.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy source and build the JAR. -DskipTests skips unit tests
# during the Docker build (tests run in CI, not here).
COPY src ./src
RUN mvn package -DskipTests -B

# ─────────────────────────────────────────────────────────────────────
# STAGE 2: RUN
# Uses a minimal JRE-only image — no compiler, no Maven, no source code.
# Final image is ~200MB instead of ~600MB.
# ─────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy only the fat JAR produced by Stage 1
COPY --from=build /app/target/*.jar app.jar

# Expose the port Spring Boot listens on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]