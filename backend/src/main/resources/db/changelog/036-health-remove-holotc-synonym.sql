--liquibase formatted sql

--changeset oriol:health-seed-009
--comment Holotranscobalamin (active B12) is a distinct analyte, not a B12 synonym; removing it avoids collapsing two measurements into one series
UPDATE analytes SET synonyms = array_remove(synonyms, 'Holotranscobalamin') WHERE code = 'vitamin_b12';
