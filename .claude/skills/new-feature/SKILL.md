---
name: new-feature
description: Scaffold a feature inside a bounded context following hexagonal architecture and strict TDD (failing domain test first, then domain, application, adapters). Use when adding any new behavior to training, health, finance, chat or ingestion.
---

# New feature (hexagonal + TDD)

Work inside ONE bounded context (`cat.subi.itaca.<context>`). Never touch another
context's internals; integrate via Modulith events if needed.

## Order of work (strict)

1. **Failing domain test first** in `src/test/kotlin/.../<context>/domain/` — pure
   JUnit, no Spring. Run it, watch it fail.
2. **Domain**: value objects / entities / domain services in `<context>/domain`.
   Idiomatic Kotlin: `@JvmInline value class` for single-value VOs, `data class`,
   `sealed interface` for results. `require(...)` for invariants. Make the test pass.
3. **Application**: use case service in `<context>/application`. Commands go through
   the domain; queries may read SQL directly (light CQRS) returning DTOs.
   Chat-exposed operations are `@Tool` methods here (phase 2+).
4. **Adapters**:
   - `adapter/in/rest`: thin controllers, DTOs, bean validation. springdoc picks
     them up — the OpenAPI spec is the frontend contract (then run /sync-api).
   - `adapter/out/persistence`: JPA entities/repositories. Schema change → /db-migration.
5. **Events** across contexts: publish from application service (after-commit via
   Modulith registry); consume with `@ApplicationModuleListener` in the other context.
6. **Verify architecture**: `./gradlew test --tests "cat.subi.itaca.ModularityTests"`
   must stay green. Module-isolated tests use `@ApplicationModuleTest`.

## Checklist before committing

- [ ] Domain tests written BEFORE implementation
- [ ] No imports from another context's non-exposed packages
- [ ] English code; Spanish only in UI-facing strings
- [ ] Full `./gradlew test` green
