--liquibase formatted sql

--changeset oriol:wellness-002
--comment Body composition from the Xiaomi scale, pushed by the external Zepp sync; one row per date
CREATE TABLE body_composition (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    measured_on   DATE          NOT NULL UNIQUE,
    weight_kg     NUMERIC(5,2)  NOT NULL,
    bmi           NUMERIC(4,1),
    body_fat_pct  NUMERIC(4,1),
    muscle_kg     NUMERIC(5,2),
    water_pct     NUMERIC(4,1),
    bone_kg       NUMERIC(4,2),
    visceral_fat  NUMERIC(4,1),
    bmr_kcal      INT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
--rollback DROP TABLE body_composition;
