--liquibase formatted sql

--changeset oriol:nutrition-001
--comment Nutrition context meals; replaces the unused health-era meals table (never wired up)
DROP TABLE IF EXISTS meals;
CREATE TABLE meals (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date        DATE        NOT NULL,
    meal_type   TEXT        NOT NULL CHECK (meal_type IN ('breakfast', 'lunch', 'dinner', 'snack')),
    description TEXT        NOT NULL,
    on_plan     BOOLEAN,
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_meals_date ON meals (date DESC);
--rollback DROP TABLE meals;
