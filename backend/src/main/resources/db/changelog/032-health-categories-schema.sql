--liquibase formatted sql

--changeset oriol:health-009
--comment Theme categorization: a panel per analyte (groups the chart) and a theme per report/document (filters the lists)
ALTER TABLE analytes ADD COLUMN category TEXT;

ALTER TABLE lab_reports ADD COLUMN category TEXT
    CHECK (category IS NULL OR category IN ('ibd', 'fertility', 'general', 'other'));

ALTER TABLE medical_documents ADD COLUMN category TEXT
    CHECK (category IS NULL OR category IN ('ibd', 'fertility', 'general', 'other'));
--rollback ALTER TABLE medical_documents DROP COLUMN category; ALTER TABLE lab_reports DROP COLUMN category; ALTER TABLE analytes DROP COLUMN category;
