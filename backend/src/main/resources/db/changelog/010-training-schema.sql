--liquibase formatted sql

--changeset oriol:training-001
--comment Esquema del contexto training: ejercicios, rutinas, workouts y series
CREATE TABLE exercises (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          TEXT        NOT NULL UNIQUE,
    grupo_muscular  TEXT        NOT NULL
);

CREATE TABLE routines (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  TEXT NOT NULL UNIQUE
);

CREATE TABLE routine_exercises (
    routine_id   BIGINT  NOT NULL REFERENCES routines (id),
    exercise_id  BIGINT  NOT NULL REFERENCES exercises (id),
    posicion     INT     NOT NULL,
    PRIMARY KEY (routine_id, exercise_id),
    UNIQUE (routine_id, posicion)
);

CREATE TABLE workouts (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha       DATE    NOT NULL,
    routine_id  BIGINT  NOT NULL REFERENCES routines (id),
    notas       TEXT,
    completado  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_workouts_fecha ON workouts (fecha DESC);

CREATE TABLE sets (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workout_id   BIGINT        NOT NULL REFERENCES workouts (id) ON DELETE CASCADE,
    exercise_id  BIGINT        NOT NULL REFERENCES exercises (id),
    peso         NUMERIC(6,2)  NOT NULL CHECK (peso >= 0),
    reps         INT           NOT NULL CHECK (reps > 0),
    orden        INT           NOT NULL,
    rpe          NUMERIC(3,1)  CHECK (rpe BETWEEN 1 AND 10),
    UNIQUE (workout_id, orden)
);

CREATE INDEX idx_sets_exercise ON sets (exercise_id);
--rollback DROP TABLE sets; DROP TABLE workouts; DROP TABLE routine_exercises; DROP TABLE routines; DROP TABLE exercises;
