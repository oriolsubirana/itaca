--liquibase formatted sql

--changeset oriol:nutrition-002
--comment Estimated calories per meal (from photo analysis or manual entry)
ALTER TABLE meals ADD COLUMN calories INT;
--rollback ALTER TABLE meals DROP COLUMN calories;
