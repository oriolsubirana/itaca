--liquibase formatted sql

--changeset oriol:training-006
--comment Strava calories live only on the activity detail endpoint; store them once fetched
ALTER TABLE activities ADD COLUMN calories INT;
--rollback ALTER TABLE activities DROP COLUMN calories;
