--liquibase formatted sql

--changeset oriol:health-003
--comment Original filename of the uploaded lab report, for recognizable pending rows in the UI
ALTER TABLE lab_reports ADD COLUMN filename TEXT;
--rollback ALTER TABLE lab_reports DROP COLUMN filename;
