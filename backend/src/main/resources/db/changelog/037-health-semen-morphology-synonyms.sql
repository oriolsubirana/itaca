--liquibase formatted sql

--changeset oriol:health-seed-010
--comment Sperm morphology is printed as "Normal forms"/"Normale Formen" (% normal forms), not "morphology"; add those synonyms
UPDATE analytes SET synonyms = synonyms || '{"normal forms","% normal forms","normale Formen","Normalformen","formes normals","formas normales (Kruger)","Kruger"}'::text[] WHERE code = 'semen_morphology';
