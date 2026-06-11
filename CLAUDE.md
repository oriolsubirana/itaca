# Ítaca — project instructions

Single-user personal dashboard (health/IBD, training, finance) for Oriol. The main
interface is a chat with Claude that reads AND writes data (gym sessions guided and
logged by chat). This project doubles as an architecture gym: enterprise-grade quality.

## Language convention (strict)

- **Everything in code is English**: identifiers, DB schema (tables, columns, check
  values), tests, comments, logs, commit messages.
- **Spanish only for**: UI/chat texts and user-facing seed data (exercise names,
  analyte display names). README is in Spanish.

## Docs over memory (strict)

This project runs the newest version of everything (Boot 4.1, Modulith 2.1,
Spring AI 2.0-RC, Testcontainers 2.x, Tailwind 4, ESLint 10, Vite 8...). Claude's
training knowledge skews to OLDER versions of these libraries, so for ANY
integration point, configuration property, annotation or artifact name:

1. **Check the official docs for the exact pinned version first** (WebFetch) —
   e.g. https://docs.spring.io/spring-boot/4.1/ — not from memory.
2. Verify artifact names/versions against Maven Central metadata or the POM
   itself (`repo1.maven.org`; the search API is blocked, metadata XML works).
3. If memory and docs disagree, the docs win. Record the discovery under
   "Version gotchas" below.
4. The `/check-docs` skill codifies this workflow — use it.

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
  `testcontainers-junit-jupiter`, `testcontainers-bom`. Classes moved to
  `org.testcontainers.postgresql` (no `SELF` generic); the old
  `org.testcontainers.containers` package is a deprecated shim.
- Kotlin 2.2.x cannot emit JVM 25 bytecode: toolchain/runtime Java 25, `jvmTarget` 24.
- Spring AI 2.0 GA is not out yet (latest: `2.0.0-RC2` on Maven Central). Check
  before phase 2. Spring AI 2.0 API differs from 1.x — always check current docs.
- No `io.spring.dependency-management`: use `platform(SpringBootPlugin.BOM_COORDINATES)`.
- detekt 1.x's embedded analyzer rejects JVM target 25: its tasks pin `jvmTarget = "21"`.
- detekt 1.23.8 triggers Gradle's `ReportingExtension.file(String)` deprecation
  warning at apply time. Upstream issue, harmless until Gradle 10; do not chase it —
  Renovate will bring the fixed release.
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
2. ✅ Chat + workout mode end-to-end (Spring AI 2.0-RC2, training tools, SSE, mobile UI)
3. Health (chat+form diary, lab pipeline with JobRunr)
4. Home/dashboard · 5. Finance (CSV import) · 6. Ingestion (`/api/ingest`)

### Chat architecture (phase 2)

- Cross-context tool wiring without coupling: `shared.chat.ChatTools` marker
  interface; each context's application service implements it (`TrainingTools`);
  the chat module injects `List<ChatTools>` and passes them to `ChatClient.tools()`.
- Spring AI 2.0 notes: property is `spring.ai.anthropic.chat.model` (no `.options`),
  tools via `@Tool`/`@ToolParam` (`org.springframework.ai.tool.annotation`),
  `ChatClient` fluent: `.system().messages().tools().stream().content()` → Flux.
- Chat history is persisted in `chat_messages` (not Spring AI's memory advisors);
  `ChatService` replays the last 60 messages per session.
- SSE: MVC `SseEmitter` bridged from the Flux; events `chunk`/`done`/`error`,
  payload `{"text": ...}`.
- `adapter/in` packages need `@file:Suppress("ktlint:standard:package-name")`
  (`in` is a Kotlin keyword).
- Mocking `ChatModel` in tests: also stub `getOptions()`/`getDefaultOptions()`.
- JPA + JdbcTemplate read-side in one transaction: `saveAndFlush` before reading.
