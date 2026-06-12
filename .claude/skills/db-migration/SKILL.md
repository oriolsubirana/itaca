---
name: db-migration
description: Create a Liquibase changeset following Ítaca's conventions (formatted SQL, English schema, per-context numbering, rollback, seed test updates). Use whenever the DB schema or seeds change.
---

# DB migration

Create or modify Liquibase changelogs in `backend/src/main/resources/db/changelog/`.

## Conventions

1. **Formatted SQL** files (`--liquibase formatted sql`), included from
   `db.changelog-master.yaml` in order.
2. Numbering by bounded context: `01x-training`, `02x-health`, `03x-finance`,
   `04x-chat`, `05x-ingestion`. Schema and seeds in separate files (`-schema` / `-seed`).
3. Changeset ids: `oriol:<context>-NNN` (schema) or `oriol:<context>-seed-NNN`.
   **Never edit an already-pushed changeset** (checksum break) — add a new one.
4. Schema entirely in **English** (tables, columns, check values). User-facing seed
   data (exercise names, analyte display names) stays in Spanish.
5. No FKs across bounded contexts — ever. Reference by id without constraint if a
   context must store another context's id (prefer events instead).
6. Include `--rollback` for schema changesets.
7. Style: `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`, `TEXT` over VARCHAR,
   `TIMESTAMPTZ`, CHECK constraints for enum-like values, indexes for known queries.

## After the change

1. Update `SchemaAndSeedIntegrationTest` if seeds/tables changed (counts, key rows).
2. Run `cd backend && ./gradlew test --tests "cat.subi.itaca.SchemaAndSeedIntegrationTest"`
   (needs Docker; the session hook leaves it ready).
3. If a JPA entity maps the table, keep entity and changelog in sync (ddl-auto is
   `none`; Liquibase is the only source of truth).
