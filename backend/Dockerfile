# Stage 1: build with the Gradle wrapper and JDK 25
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon bootJar

# Stage 2: minimal runtime with JRE 25
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 itaca
USER itaca

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
