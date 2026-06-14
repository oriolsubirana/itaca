--liquibase formatted sql

--changeset oriol:training-005
--comment Link a logged strength workout to its same-day Strava gym activity so each view enriches the other
ALTER TABLE workouts ADD COLUMN strava_id BIGINT;
CREATE INDEX idx_workouts_strava ON workouts (strava_id);
--rollback DROP INDEX idx_workouts_strava; ALTER TABLE workouts DROP COLUMN strava_id;
