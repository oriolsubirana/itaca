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
Spring AI 2.0, Testcontainers 2.x, Tailwind 4, ESLint 10, Vite 8...). Claude's
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
  Headless UI · vite-plugin-pwa · Recharts. Mobile-first (90% phone usage), bottom
  tab bar, ≥44px touch targets. Aesthetic: Margaret Howell — warm neutrals, clean
  type, zero noise. **Interactive components (dropdowns, dialogs, popovers, tabs,
  switches...) always use Headless UI primitives** styled with Tailwind
  data-attributes (`data-focus`/`data-selected`/`data-open`) — never raw
  `<select>`/`role="dialog"` reimplementations, and no styled component libraries
  (Material UI etc.: their style engines fight Tailwind and the aesthetic).
  Page width: chat/reading pages stay `max-w-2xl` (line-length readability);
  dashboard pages (Home/Salud/Gym/Finanzas, phases 3-5) use a wider responsive
  grid (`max-w-5xl`+, multi-column cards, Recharts in ResponsiveContainer) — move
  the max-width from Layout to per-page when the first dashboard lands.
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
- Spring AI 2.0 reached GA (`2.0.0` on Maven Central). Its API differs from 1.x
  — always check current docs.
- No `io.spring.dependency-management`: use `platform(SpringBootPlugin.BOM_COORDINATES)`.
- detekt 1.x's embedded analyzer rejects JVM target 25: its tasks pin `jvmTarget = "21"`.
- detekt 1.23.8 triggers Gradle's `ReportingExtension.file(String)` deprecation
  warning at apply time. Upstream issue, harmless until Gradle 10; do not chase it —
  Renovate will bring the fixed release.
- ESLint 10 flat config: react-hooks flat preset is `configs.flat.recommended`.
- Boot 4 split MockMvc test support: dependency `spring-boot-starter-webmvc-test`,
  annotation `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`
  (the old `...test.autoconfigure.web.servlet` package is gone).

## Architecture rules

- Bounded contexts = Modulith modules: `training`, `health`, `finance`, `chat`,
  `ingestion`, `nutrition`, `shared` (only open module). No direct references or FKs across
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
2. ✅ Chat + workout mode end-to-end (Spring AI 2.0, training tools, SSE, mobile UI)
3. ✅ Health (diary+flares via chat and form; lab pipeline: upload → JobRunr → claude-haiku extraction → review → per-analyte chart)
4. Home/dashboard · 5. ✅ Finance (CSV import) · 6. ✅ Ingestion (`/api/ingest`)
7. ✅ Nutrition (anti-inflammatory paleo: chat proposals adjusted to training/flares + meal logging)

### Nutrition architecture (phase 7)

- Own bounded context `nutrition`; `NutritionService` is a `ChatTools` impl (auto-wired into
  the chat's `List<ChatTools>`), exposing `log_meal` / `query_meals` and reused by the REST
  adapter (`/api/nutrition/meals` GET/POST/DELETE). `meals` table (date, meal_type, on_plan)
  replaces the unused health-era `meals` table (migration 060 drops + recreates it).
- The meal PROPOSALS are not code: they are generated by the model from a NUTRITION section
  in `SystemPrompts.COMMON`. Claude adjusts to sport (`query_workouts`/`query_activities`)
  and active flares (`query_health`), follows the anti-inflammatory paleo profile, and logs
  what the user ate via `log_meal` (setting `onPlan`). The health rule still holds: these are
  food proposals, not medical treatment — clinical questions go to the gastroenterologist.

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
- Persistent user memory: `chat_memories` table + `save_memory`/`forget_memory`
  tools; `ChatService` injects all memories into the system prompt. The prompt
  forbids claiming "anotado" without a write-tool call in the same turn.
- Spring AI autoconfigures beans named `chatMemory`/`chatMemoryRepository` —
  do not name your own beans that (our JPA repo is `UserMemoryRepository`).
- JPA + JdbcTemplate read-side in one transaction: `saveAndFlush` before reading.

### Health pipeline notes (phase 3)

- JobRunr jobs use the JobRequest/JobRequestHandler pattern (no lambda bytecode
  analysis, Kotlin-safe). `JobRequestScheduler` is autoconfigured by the starter.
- PDF to Claude: `.user { it.text(prompt).media(pdfMimeType, ByteArrayResource(bytes)) }`;
  structured output via `.entity(Class)` — extraction DTOs use `var` + defaults
  so any Jackson can bind them.
- Per-request model override: `.options(AnthropicChatOptions.builder().model(...))`
  (pass the Builder itself, not `.build()`).
- Storage port `DocumentStorage` lives in `shared.storage` (used by health and
  ingestion): local files by default; Supabase impl activates when the SUPABASE_URL env var
  exists (relaxed binding to `supabase.url` — do NOT add a `supabase.url:` default
  to application.yml or the conditional always matches). `loadFile` returns a
  `StoredFile(filename, content)`.
- Only CONFIRMED lab reports feed analyte series; review gate is mandatory.
- Analyte normalization is two-tier: a cheap deterministic `AnalyteMatcher`
  (canonical-form exact match: accent-fold, decoration-strip, synonyms) runs
  first; `SemanticAnalyteMatcher` (Claude, "Normalizar con IA" button) maps the
  multilingual long tail to canonical codes. Define a canonical analyte once — the
  model handles the language variants, so don't chase per-language synonyms. The
  unit guard and review gate remain backstops over both.
- Spring Data derived `deleteBy...` loads and deletes row by row → StaleObjectState
  when jobs race; use `@Modifying @Query` bulk deletes and serialize concurrent
  extraction jobs with a `PESSIMISTIC_WRITE` row lock taken AFTER the slow API call.

### Ingestion architecture (phase 6)

- One generic entry point `POST /api/ingest` (multipart, for the iOS Shortcut / web
  inbox): store via `DocumentStorage`, register in `ingested_files` (status pending),
  enqueue a JobRunr `ProcessIngestionRequest`. `GET /api/ingest` is the inbox;
  `POST /api/ingest/{id}/retry` re-queues a failed file.
- Two-tier routing (same shape as analyte normalization): deterministic
  `IngestionRouter` (CSV → finance; PDF by filename markers) first; the ambiguous PDF
  long tail falls through to `AnthropicIngestionClassifier` (claude-haiku reads the PDF,
  answers LAB/FINANCE). CSVs never hit the model.
- **First cross-context Modulith events in the codebase.** They are ingestion's exposed
  API and live in its top-level package (`cat.subi.itaca.ingestion`), NOT a subpackage:
  `LabReportReceived`/`BankStatementReceived` (ingestion → health/finance) and
  `IngestionSucceeded`/`IngestionFailed` (health/finance → ingestion, flip the row).
  Owning all four in ingestion keeps the module graph acyclic (ingestion depends on
  nobody; health/finance depend on ingestion + shared) — `ModularityTests` stays green.
- Events carry ids + the storage path only, never bytes — consumers reload via the
  shared `DocumentStorage`, keeping the JDBC event registry (outbox) light.
- `@ApplicationModuleListener` (after-commit, async, registry-backed redelivery) is the
  consumer; no `@EnableAsync` needed. Listeners must be idempotent. They catch broadly
  (`@Suppress("TooGenericExceptionCaught")`) to turn ANY downstream failure into an
  `IngestionFailed` the inbox can show.
- Test the publish synchronously with `@RecordApplicationEvents` + `ApplicationEvents`
  (the event is recorded at `publishEvent`, before the async listeners run).
