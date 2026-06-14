--liquibase formatted sql

--changeset oriol:training-003
--comment Allow 'gym' activity type (Strava weight-training/workout sessions, distinct from 'hike')
ALTER TABLE activities DROP CONSTRAINT activities_type_check;
ALTER TABLE activities ADD CONSTRAINT activities_type_check
    CHECK (type IN ('bike', 'run', 'hike', 'gym', 'other'));
--rollback ALTER TABLE activities DROP CONSTRAINT activities_type_check;
--rollback ALTER TABLE activities ADD CONSTRAINT activities_type_check CHECK (type IN ('bike', 'run', 'hike', 'other'));
