---
name: modulith-event
description: Add a cross-context domain event end-to-end with Spring Modulith (event record, after-commit publication via the JDBC registry/outbox, listener in the consuming context, scenario tests). Use when two bounded contexts need to communicate.
---

# Modulith domain event

Contexts never call each other — they publish events. Planned events:
`WorkoutCompleted` (training → health/home stats), `LabResultsImported`,
`FlareStarted/Ended` (health), file-processed events (ingestion → health/finance).

## Steps

1. **Define the event** in the producing context's top-level package (it is part of
   the module's exposed API), as an immutable record:
   ```kotlin
   // cat.subi.itaca.training (NOT in an internal subpackage)
   data class WorkoutCompleted(val workoutId: Long, val routineName: String, val date: LocalDate)
   ```
   Carry ids + primitive facts, never entities from the producer.
2. **Publish** from the producing application service inside the transaction
   (`ApplicationEventPublisher.publishEvent`). Modulith's JDBC registry persists it
   in the same commit (outbox) — `event_publication` table already exists.
3. **Consume** in the other context with:
   ```kotlin
   @ApplicationModuleListener
   fun on(event: WorkoutCompleted) { ... }
   ```
   Runs after commit, async, with registry-backed redelivery
   (`republish-outstanding-events-on-restart` is enabled). Handlers must be
   **idempotent** — they can be redelivered.
4. **Test**:
   - Producer: `@ApplicationModuleTest` + `Scenario` API —
     `scenario.stimulate(...).andWaitForEventOfType(WorkoutCompleted::class.java)...`
   - Consumer: `@ApplicationModuleTest` publishing the event and asserting the
     side effect.
   - `ModularityTests` must stay green (event types are the only shared surface).
5. **Failure handling**: incomplete publications stay in `event_publication`; do not
   build manual retry — the registry owns redelivery.
