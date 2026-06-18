--liquibase formatted sql

--changeset oriol:nutrition-003
--comment Estimated macros per meal (e.g. "P 42 · C 46 · G 26"), from photo/text analysis
ALTER TABLE meals ADD COLUMN macros TEXT;
--rollback ALTER TABLE meals DROP COLUMN macros;
