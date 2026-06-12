---
name: spring-upgrade
description: Safely check and apply dependency upgrades (Spring Boot, Modulith, Spring AI, Kotlin, JobRunr, frontend packages) verifying real versions on Maven Central/npm and running the full suites. Use for periodic upgrades or when a new GA (e.g. Spring AI 2.0) lands.
---

# Dependency upgrade

## Backend

1. Current pins live in `backend/build.gradle.kts`. For each managed coordinate,
   check the latest release:
   ```bash
   curl -s "https://repo1.maven.org/maven2/<group-path>/<artifact>/maven-metadata.xml" | grep -oPm1 "(?<=<release>)[^<]+"
   ```
   Key alignment rules:
   - Spring Modulith minor tracks Boot minor (Boot 4.1 ↔ Modulith 2.1).
   - JobRunr starter must match Boot major (`jobrunr-spring-boot-4-starter`).
   - springdoc 3.x is the Boot 4 line.
   - Kotlin: stay on the 2.2.x patch line unless Oriol approves a minor bump;
     if Kotlin finally supports `JvmTarget.JVM_25`, also bump `jvmTarget` and
     remove the `options.release = 24` workaround.
   - Spring AI: pinned milestone/RC until GA; switch to GA as soon as it exists.
2. Apply, then run the FULL suite: `cd backend && ./gradlew clean build`.
3. Read release notes (WebFetch) for majors/minors before applying — Boot 4.x
   moves autoconfiguration into per-technology starters; a green compile does not
   guarantee runtime autoconfig still triggers (see Liquibase gotcha in CLAUDE.md).
   The `SchemaAndSeedIntegrationTest` catches silent autoconfig regressions.

## Frontend

1. `cd frontend && npm outdated`. Respect peer ranges (e.g. openapi-typescript
   currently requires TS ^5.x).
2. `npm install <pkg>@latest ...`, then `npm run build` (typecheck + build + PWA).

## Always

- One commit per coherent upgrade batch, listing old → new versions.
- If something breaks, prefer fixing forward over pinning back; ask Oriol if the
  fix requires API changes beyond mechanical migration.
