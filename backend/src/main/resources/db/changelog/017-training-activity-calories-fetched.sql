--liquibase formatted sql

--changeset oriol:training-007
--comment Mark whether the calories detail call already ran, so activities Strava reports without calories are not re-fetched every sync
ALTER TABLE activities ADD COLUMN calories_fetched BOOLEAN NOT NULL DEFAULT FALSE;
--rollback ALTER TABLE activities DROP COLUMN calories_fetched;
