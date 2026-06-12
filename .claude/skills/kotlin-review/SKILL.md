---
name: kotlin-review
description: Review Kotlin code for idiomatic style and project conventions (value classes, sealed results, null-safety, no over-engineering, Spring-friendly Kotlin). Use before committing significant backend changes or on request.
---

# Kotlin idiom review

Review the current diff (`git diff` / `git diff --staged`) against these criteria.
Report findings with file:line; apply fixes if asked.

## Domain modeling

- Single-value concepts → `@JvmInline value class` with a private constructor and
  validating `companion object` factory (see `Weight`, `Reps`, `BristolScale`).
- Operation results that can fail in expected ways → `sealed interface` hierarchies,
  not exceptions; `require()` only for programmer-error invariants.
- Prefer immutable `data class` + `copy` over mutable state. No `lateinit` in domain.
- `BigDecimal` for money/weights — never Double in domain state (Double only as an
  input convenience, converted immediately).

## Null-safety and flow

- No `!!`. Prefer `?:` early returns, `?.let`, or redesign the type.
- Exhaustive `when` over sealed types — no `else` branch that hides new cases.

## Spring + Kotlin specifics

- Constructor injection only; no `@Autowired` on fields.
- `allOpen` is configured for JPA annotations only — domain classes stay final.
- JPA entities live in `adapter/out/persistence`, never in `domain`. Map explicitly.
- Jackson 3 (`tools.jackson.module.kotlin`) handles data classes; don't add
  default-arg constructors for serialization.
- Coroutines only where they pay off (concurrent Anthropic calls, job orchestration)
  — controllers stay blocking MVC on virtual threads.

## Simplicity (anti-over-engineering)

- No interfaces with a single implementation unless an adapter boundary requires it.
- No generic abstractions "for the future". Three concrete usages before abstracting.
- Functions small, names explicit, comments only for non-obvious constraints.
