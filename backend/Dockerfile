# Multi-stage so `docker compose up --build` works from a clean checkout —
# no need to run `mvn package` on the host first.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Copy the POM first so Maven's dependency layer is cached and only
# re-resolved when the POM actually changes, not on every source edit.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ── runtime ──────────────────────────────────────────────────────────
# JRE only: no compiler or Maven in the shipped image.
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /src/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
