--liquibase formatted sql

--changeset oriol:profile-001
--comment Single-row profile: anthropometrics for the calorie-target calculation
CREATE TABLE profile (
    id             INT         PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    weight_kg      NUMERIC(5, 1),
    height_cm      INT,
    birth_date     DATE,
    sex            TEXT        CHECK (sex IN ('male', 'female')),
    activity_level TEXT        CHECK (activity_level IN ('sedentary', 'light', 'moderate', 'active', 'very_active')),
    goal           TEXT        CHECK (goal IN ('lose', 'maintain', 'gain')),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO profile (id) VALUES (1);
--rollback DROP TABLE profile;
