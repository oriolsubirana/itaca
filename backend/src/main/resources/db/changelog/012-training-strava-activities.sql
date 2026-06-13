--liquibase formatted sql

--changeset oriol:training-002
--comment Strava connection (single account) and imported endurance activities (bike/run/hike) from the Garmin -> Strava bridge
CREATE TABLE strava_account (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    athlete_id     BIGINT,
    access_token   TEXT,
    refresh_token  TEXT        NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE activities (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    strava_id      BIGINT      NOT NULL UNIQUE,
    type           TEXT        NOT NULL CHECK (type IN ('bike', 'run', 'hike', 'other')),
    name           TEXT,
    start_date     TIMESTAMPTZ NOT NULL,
    distance_m     NUMERIC(10,1),
    moving_time_s  INT,
    elevation_m    NUMERIC(8,1),
    avg_hr         NUMERIC(5,1),
    avg_speed_ms   NUMERIC(6,3)
);

CREATE INDEX idx_activities_start ON activities (start_date DESC);
--rollback DROP TABLE activities; DROP TABLE strava_account;
