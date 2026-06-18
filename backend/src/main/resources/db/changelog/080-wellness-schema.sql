--liquibase formatted sql

--changeset oriol:wellness-001
--comment Daily Garmin wellness metrics (sleep, HRV, recovery), one row per date (upsert)
CREATE TABLE daily_wellness (
    date              DATE PRIMARY KEY,
    sleep_minutes     INT,
    deep_minutes      INT,
    light_minutes     INT,
    rem_minutes       INT,
    awake_minutes     INT,
    sleep_score       INT,
    hrv_avg_ms        INT,
    hrv_status        TEXT,
    resting_hr        INT,
    stress_avg        INT,
    body_battery_high INT,
    body_battery_low  INT,
    steps             INT,
    active_calories   INT,
    spo2_avg          INT,
    respiration_avg   NUMERIC(4, 1),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE daily_wellness;
