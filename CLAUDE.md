# Ítaca — project instructions

Single-user personal dashboard (health/IBD, training, finance) for Oriol. The main
interface is a chat with Claude that reads AND writes data (gym sessions guided and
logged by chat). This project doubles as an architecture gym: enterprise-grade quality.

## Language convention (strict)

- **Everything in code is English**: identifiers, DB schema (tables, columns, check
  values), tests, comments, logs, commit messages.
- **Spanish only for**: UI/chat texts and user-facing seed data (exercise names,
  analyte display names). README is in Spanish.

## Stack (pinned — do not change without asking)

- Backend: Kotlin 2.2.x · Spring Boot 4.1 · Java 25 · Gradle 9 (Kotlin DSL) ·
  Spring Modulith 2.1 · Spring MVC + virtual threads (NO WebFlux) · JPA/Hibernate ·
  Liquibase · JobRunr (same Postgres, no broker) · springdoc · Spring AI 2.0 (phase 2)
- Frontend: React 19 · Vite · TypeScript · TanStack Router/Query · Tailwind v4 ·
  vite-plugin-pwa · Recharts. Mobile-first (90% phone usage), bottom tab bar, ≥44px
  touch targets. Aesthetic: Margaret Howell — warm neutrals, clean type, zero noise.
- Postgres (Supabase in prod) · Supabase Storage via REST.

### Version gotchas already learned (do not re-derive)

- Boot 4 modularized its starters: Liquibase needs `spring-boot-starter-liquibase`
  (just `liquibase-core` will NOT auto-run migrations).
- Boot 4 uses **Jackson 3** (`tools.jackson.*`), not 2 (`com.fasterxml.*`).
- Testcontainers is 2.x: artifacts are `testcontainers-postgresql`,
  `testcontainers-junit-jupiter`, `testcontainers-bom`.
- Kotlin 2.2.x cannot emit JVM 25 bytecode: toolchain/runtime Java 25, `jvmTarget` 24.
- Spring AI 2.0 GA is not out yet (latest: `2.0.0-RC2` on Maven Central). Check
  before phase 2. Spring AI 2.0 API differs from 1.x — always check current docs.
- No `io.spring.dependency-management`: use `platform(SpringBootPlugin.BOM_COORDINATES)`.
- detekt 1.x's embedded analyzer rejects JVM target 25: its tasks pin `jvmTarget = "21"`.
- ESLint 10 flat config: react-hooks flat preset is `configs.flat.recommended`.

## Architecture rules

- Bounded contexts = Modulith modules: `training`, `health`, `finance`, `chat`,
  `ingestion`, `shared` (only open module). No direct references or FKs across
  contexts; communicate via Modulith events (JDBC registry = outbox). Verified by
  `ModularityTests` — keep it green.
- Hexagonal per context: `domain` / `application` / `adapter/in/rest`,
  `adapter/out/{persistence,anthropic,storage}`.
- Light CQRS: command handlers go through the domain; query handlers read SQL
  directly and return DTOs. No event sourcing.
- **Strict TDD for domain logic**: write the failing test first. Pure domain tests
  without Spring; adapters with Testcontainers/WireMock; `@ApplicationModuleTest`
  per context.
- Idiomatic Kotlin: data/value/sealed classes, null-safety. Simple over abstract.
- Security (current phase): static bearer token filter in `shared`. Do NOT add
  Spring Security until the auth phase.

## Domain rules that must never be violated

- Health: Claude never gives medical advice or interprets diagnoses — only records,
  retrieves and describes data, suggesting to discuss with the gastroenterologist.
- Training (workout-mode prompt, phase 2): goal is definition + functional strength
  for cycling (NOT max hypertrophy); working sets 3×6-8 with 90s rest; NEVER suggest
  45° leg press (left glute injury); conservative progression (+2.5kg only after
  exceeding target reps with margin).
- Anthropic API key lives only in the backend (env var).

## Commands

```bash
cd backend && ./gradlew build         # compile + ktlint + detekt + full tests (needs Docker)
cd backend && ./gradlew ktlintFormat  # auto-fix formatting
cd backend && ./gradlew bootRun       # needs Postgres: docker compose up -d postgres
cd frontend && npm run lint           # eslint
cd frontend && npm run build          # typecheck + vite build
cd frontend && npm run generate:api   # regenerate TS client from running backend
```

Linters are CI gates: leave `ktlintCheck`, `detekt` and `npm run lint` green.

The web session environment (dockerd, JDK 25, Testcontainers mirror, caches) is
prepared by `.claude/hooks/session-start.sh`.

## Roadmap

1. ✅ Skeleton (modules, schema+seeds, compose, CI)
2. Chat + workout mode end-to-end (the star feature)
3. Health (chat+form diary, lab pipeline with JobRunr)
4. Home/dashboard · 5. Finance (CSV import) · 6. Ingestion (`/api/ingest`)
