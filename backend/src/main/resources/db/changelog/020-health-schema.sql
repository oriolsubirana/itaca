--liquibase formatted sql

--changeset oriol:health-001
--comment Health context schema: IBD diary, meals, flares and lab results
CREATE TABLE diary_entries (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date             DATE    NOT NULL UNIQUE,
    bristol          INT     CHECK (bristol BETWEEN 1 AND 7),
    pain             INT     CHECK (pain BETWEEN 0 AND 10),
    urgency          INT     CHECK (urgency BETWEEN 0 AND 10),
    blood            BOOLEAN NOT NULL DEFAULT FALSE,
    bowel_movements  INT     CHECK (bowel_movements >= 0),
    stress           INT     CHECK (stress BETWEEN 0 AND 10),
    notes            TEXT
);

CREATE TABLE meals (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    diary_entry_id  BIGINT  NOT NULL REFERENCES diary_entries (id) ON DELETE CASCADE,
    description     TEXT    NOT NULL,
    tags            TEXT[]  NOT NULL DEFAULT '{}'
);

CREATE TABLE flares (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    start_date  DATE NOT NULL,
    end_date    DATE CHECK (end_date IS NULL OR end_date >= start_date),
    severity    TEXT NOT NULL CHECK (severity IN ('mild', 'moderate', 'severe')),
    notes       TEXT
);

--changeset oriol:health-002
--comment Analyte dictionary and lab results
CREATE TABLE analytes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            TEXT    NOT NULL UNIQUE,
    name            TEXT    NOT NULL,
    canonical_unit  TEXT    NOT NULL,
    synonyms        TEXT[]  NOT NULL DEFAULT '{}'
);

CREATE TABLE lab_reports (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date          DATE NOT NULL,
    laboratory    TEXT,
    storage_path  TEXT,
    status        TEXT NOT NULL DEFAULT 'pending_review'
                  CHECK (status IN ('pending_review', 'confirmed', 'discarded')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE lab_results (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lab_report_id  BIGINT  NOT NULL REFERENCES lab_reports (id) ON DELETE CASCADE,
    analyte_id     BIGINT  REFERENCES analytes (id),
    raw_name       TEXT    NOT NULL,
    value          NUMERIC(12,3) NOT NULL,
    unit           TEXT,
    ref_min        NUMERIC(12,3),
    ref_max        NUMERIC(12,3)
);

CREATE INDEX idx_lab_results_analyte ON lab_results (analyte_id);
--rollback DROP TABLE lab_results; DROP TABLE lab_reports; DROP TABLE analytes; DROP TABLE flares; DROP TABLE meals; DROP TABLE diary_entries;
