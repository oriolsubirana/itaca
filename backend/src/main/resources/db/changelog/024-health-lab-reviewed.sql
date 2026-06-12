--liquibase formatted sql

--changeset oriol:health-005
--comment Per-result review checkmark to track progress while validating an extraction
ALTER TABLE lab_results ADD COLUMN reviewed BOOLEAN NOT NULL DEFAULT FALSE;
--rollback ALTER TABLE lab_results DROP COLUMN reviewed;
