# Single same-origin image: build the React SPA, bundle it into the Spring Boot jar's static
# resources, and run the jar. Build context is the repo root (see fly.toml).

# Stage 1 — frontend
FROM node:24-alpine AS frontend
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2 — backend (with the SPA copied into src/main/resources/static so bootJar bundles it)
FROM eclipse-temurin:25.0.4_7-jdk AS build
WORKDIR /app
COPY backend/gradlew ./
COPY backend/gradle ./gradle
COPY backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.properties ./
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY backend/src ./src
COPY --from=frontend /web/dist ./src/main/resources/static
RUN ./gradlew --no-daemon bootJar

# Stage 3 — runtime
FROM eclipse-temurin:25.0.4_7-jre
WORKDIR /app
RUN useradd --system --uid 1001 itaca
USER itaca
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
