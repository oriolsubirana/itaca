--liquibase formatted sql

--changeset oriol:document-storage-path-unique-001
--comment Backstop the registerStored check-then-insert: one health row per stored file. Postgres
--comment treats NULLs as distinct, so manually-created rows without a storage_path are unaffected.
CREATE UNIQUE INDEX lab_reports_storage_path_uq ON lab_reports (storage_path);
CREATE UNIQUE INDEX medical_documents_storage_path_uq ON medical_documents (storage_path);
--rollback DROP INDEX medical_documents_storage_path_uq;
--rollback DROP INDEX lab_reports_storage_path_uq;
