--liquibase formatted sql

--changeset oriol:health-001
--comment Esquema del contexto health: diario EII, comidas, brotes y analíticas
CREATE TABLE diary_entries (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha             DATE    NOT NULL UNIQUE,
    bristol           INT     CHECK (bristol BETWEEN 1 AND 7),
    dolor             INT     CHECK (dolor BETWEEN 0 AND 10),
    urgencia          INT     CHECK (urgencia BETWEEN 0 AND 10),
    sangre            BOOLEAN NOT NULL DEFAULT FALSE,
    num_deposiciones  INT     CHECK (num_deposiciones >= 0),
    estres            INT     CHECK (estres BETWEEN 0 AND 10),
    notas             TEXT
);

CREATE TABLE meals (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    diary_entry_id  BIGINT  NOT NULL REFERENCES diary_entries (id) ON DELETE CASCADE,
    descripcion     TEXT    NOT NULL,
    tags            TEXT[]  NOT NULL DEFAULT '{}'
);

CREATE TABLE flares (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha_inicio  DATE NOT NULL,
    fecha_fin     DATE CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
    severidad     TEXT NOT NULL CHECK (severidad IN ('leve', 'moderado', 'grave')),
    notas         TEXT
);

--changeset oriol:health-002
--comment Diccionario de analitos y resultados de laboratorio
CREATE TABLE analytes (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo           TEXT    NOT NULL UNIQUE,
    nombre           TEXT    NOT NULL,
    unidad_canonica  TEXT    NOT NULL,
    sinonimos        TEXT[]  NOT NULL DEFAULT '{}'
);

CREATE TABLE lab_reports (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha         DATE NOT NULL,
    laboratorio   TEXT,
    storage_path  TEXT,
    estado        TEXT NOT NULL DEFAULT 'pendiente_revision'
                  CHECK (estado IN ('pendiente_revision', 'confirmado', 'descartado')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE lab_results (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lab_report_id  BIGINT  NOT NULL REFERENCES lab_reports (id) ON DELETE CASCADE,
    analyte_id     BIGINT  REFERENCES analytes (id),
    analito_raw    TEXT    NOT NULL,
    valor          NUMERIC(12,3) NOT NULL,
    unidad         TEXT,
    ref_min        NUMERIC(12,3),
    ref_max        NUMERIC(12,3)
);

CREATE INDEX idx_lab_results_analyte ON lab_results (analyte_id);
--rollback DROP TABLE lab_results; DROP TABLE lab_reports; DROP TABLE analytes; DROP TABLE flares; DROP TABLE meals; DROP TABLE diary_entries;
