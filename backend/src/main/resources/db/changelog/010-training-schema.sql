--liquibase formatted sql

--changeset oriol:training-001
--comment Training context schema: exercises, routines, workouts and sets
CREATE TABLE exercises (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          TEXT NOT NULL UNIQUE,
    muscle_group  TEXT NOT NULL
);

CREATE TABLE routines (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name  TEXT NOT NULL UNIQUE
);

CREATE TABLE routine_exercises (
    routine_id   BIGINT  NOT NULL REFERENCES routines (id),
    exercise_id  BIGINT  NOT NULL REFERENCES exercises (id),
    position     INT     NOT NULL,
    PRIMARY KEY (routine_id, exercise_id),
    UNIQUE (routine_id, position)
);

CREATE TABLE workouts (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date        DATE    NOT NULL,
    routine_id  BIGINT  NOT NULL REFERENCES routines (id),
    notes       TEXT,
    completed   BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_workouts_date ON workouts (date DESC);

CREATE TABLE sets (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workout_id   BIGINT        NOT NULL REFERENCES workouts (id) ON DELETE CASCADE,
    exercise_id  BIGINT        NOT NULL REFERENCES exercises (id),
    weight_kg    NUMERIC(6,2)  NOT NULL CHECK (weight_kg >= 0),
    reps         INT           NOT NULL CHECK (reps > 0),
    position     INT           NOT NULL,
    rpe          NUMERIC(3,1)  CHECK (rpe BETWEEN 1 AND 10),
    UNIQUE (workout_id, position)
);

CREATE INDEX idx_sets_exercise ON sets (exercise_id);
--rollback DROP TABLE sets; DROP TABLE workouts; DROP TABLE routine_exercises; DROP TABLE routines; DROP TABLE exercises;
