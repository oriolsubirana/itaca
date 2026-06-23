--liquibase formatted sql

--changeset oriol:drive-seen-001
--comment Tracks Google Drive files the folder watcher has already handed to ingestion (dedupe)
CREATE TABLE drive_seen (
    file_id   TEXT PRIMARY KEY,
    name      TEXT,
    mime_type TEXT,
    seen_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE drive_seen;
