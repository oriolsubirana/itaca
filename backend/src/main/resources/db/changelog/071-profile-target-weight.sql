--liquibase formatted sql

--changeset oriol:profile-002
--comment Target weight by a date (e.g. race weight for the triathlon): the trajectory and its pace are derived, not stored
ALTER TABLE profile ADD COLUMN target_weight_kg NUMERIC(5, 1) CHECK (target_weight_kg > 0);
ALTER TABLE profile ADD COLUMN target_date DATE;
--rollback ALTER TABLE profile DROP COLUMN target_date; ALTER TABLE profile DROP COLUMN target_weight_kg;
