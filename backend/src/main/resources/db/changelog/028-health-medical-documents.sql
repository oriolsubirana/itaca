--liquibase formatted sql

--changeset oriol:health-008
--comment Clinical documents (discharge summaries, consultations...): narrative reports parsed into recorded facts, never interpreted
CREATE TABLE medical_documents (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    doc_date      DATE,
    type          TEXT,
    provider      TEXT,
    center        TEXT,
    filename      TEXT,
    storage_path  TEXT,
    full_text     TEXT,
    status        TEXT NOT NULL DEFAULT 'pending_review'
                  CHECK (status IN ('pending_review', 'confirmed', 'discarded')),
    extracting    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE medical_diagnoses (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id    BIGINT NOT NULL REFERENCES medical_documents (id) ON DELETE CASCADE,
    code           TEXT,
    label          TEXT NOT NULL,
    diagnosed_on   DATE
);

CREATE TABLE medical_medications (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id  BIGINT NOT NULL REFERENCES medical_documents (id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    dose         TEXT,
    schedule     TEXT,
    duration     TEXT,
    reason       TEXT
);

CREATE INDEX idx_medical_diagnoses_document ON medical_diagnoses (document_id);
CREATE INDEX idx_medical_medications_document ON medical_medications (document_id);
CREATE INDEX idx_medical_documents_date ON medical_documents (doc_date);
--rollback DROP TABLE medical_medications; DROP TABLE medical_diagnoses; DROP TABLE medical_documents;
