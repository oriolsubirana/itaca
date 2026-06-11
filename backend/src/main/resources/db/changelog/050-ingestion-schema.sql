--liquibase formatted sql

--changeset oriol:ingestion-001
--comment Esquema del contexto ingestion: registro de ficheros recibidos por /api/ingest
CREATE TABLE ingested_files (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    origen         TEXT        NOT NULL,
    nombre         TEXT        NOT NULL,
    tipo           TEXT        NOT NULL CHECK (tipo IN ('pdf', 'csv', 'desconocido')),
    estado         TEXT        NOT NULL DEFAULT 'pendiente'
                   CHECK (estado IN ('pendiente', 'procesado', 'error')),
    storage_path   TEXT,
    error_message  TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE ingested_files;
