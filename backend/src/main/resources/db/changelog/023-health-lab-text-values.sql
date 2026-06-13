--liquibase formatted sql

--changeset oriol:health-004
--comment Qualitative lab results (e.g. "neg", "++++"): value becomes optional, text_value holds the literal
ALTER TABLE lab_results ALTER COLUMN value DROP NOT NULL;
ALTER TABLE lab_results ADD COLUMN text_value TEXT;
ALTER TABLE lab_results ADD CONSTRAINT lab_results_value_present CHECK (value IS NOT NULL OR text_value IS NOT NULL);
--rollback ALTER TABLE lab_results DROP CONSTRAINT lab_results_value_present; ALTER TABLE lab_results DROP COLUMN text_value; ALTER TABLE lab_results ALTER COLUMN value SET NOT NULL;
