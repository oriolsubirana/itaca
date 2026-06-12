--liquibase formatted sql

--changeset oriol:ingestion-001
--comment Ingestion context schema: registry of files received via /api/ingest
CREATE TABLE ingested_files (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source         TEXT        NOT NULL,
    name           TEXT        NOT NULL,
    type           TEXT        NOT NULL CHECK (type IN ('pdf', 'csv', 'unknown')),
    status         TEXT        NOT NULL DEFAULT 'pending'
                   CHECK (status IN ('pending', 'processed', 'error')),
    storage_path   TEXT,
    error_message  TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE ingested_files;
