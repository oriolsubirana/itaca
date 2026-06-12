--liquibase formatted sql

--changeset oriol:health-007
--comment Per-result measurement date for cumulative reports (one PDF, several date columns per analyte); null inherits the report date
ALTER TABLE lab_results ADD COLUMN result_date DATE;
--rollback ALTER TABLE lab_results DROP COLUMN result_date;
