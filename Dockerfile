# ---------------------------------------------------------------------------
# CineMax booking system as a container.
#   Stage 1 builds the jar with Maven, stage 2 runs it on a small Java 17 image.
#   The application talks to MySQL; docker-compose.yml starts both.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Copy the pom first so the dependency download is cached between builds.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
# Run as an ordinary user, not as root.
RUN useradd --system --create-home --uid 10001 cinemax \
    && mkdir -p /app/data && chown -R cinemax:cinemax /app
COPY --from=build /build/target/*.jar /app/app.jar
USER cinemax
EXPOSE 8080
# The movie list is the cheapest public endpoint: if it answers, the app and the database are both up.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD ["sh", "-c", "curl -fsS http://127.0.0.1:8080/api/movies > /dev/null || exit 1"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
