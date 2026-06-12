--liquibase formatted sql

--changeset oriol:health-006
--comment Visible extraction-in-progress flag so the UI can show feedback and avoid duplicate re-extraction clicks
ALTER TABLE lab_reports ADD COLUMN extracting BOOLEAN NOT NULL DEFAULT FALSE;
--rollback ALTER TABLE lab_reports DROP COLUMN extracting;
