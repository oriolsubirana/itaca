--liquibase formatted sql

--changeset oriol:ingestion-002
--comment Per-file resolved destination context and a human-readable outcome detail
ALTER TABLE ingested_files ADD COLUMN destination TEXT;
ALTER TABLE ingested_files ADD COLUMN detail TEXT;
--rollback ALTER TABLE ingested_files DROP COLUMN destination;
--rollback ALTER TABLE ingested_files DROP COLUMN detail;
