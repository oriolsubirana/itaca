--liquibase formatted sql

--changeset oriol:training-004
--comment Keep the raw Strava sport_type so the UI can name long-tail activities (Pilates, Yoga, Swim...) instead of a generic "other"
ALTER TABLE activities ADD COLUMN sport TEXT;
--rollback ALTER TABLE activities DROP COLUMN sport;
