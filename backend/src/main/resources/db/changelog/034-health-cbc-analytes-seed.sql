--liquibase formatted sql

--changeset oriol:health-seed-007
--comment Remaining CBC differential analytes (basophils, eosinophils, reticulocytes, RDW) that Catalan/Swiss hemograms print
INSERT INTO analytes (code, name, canonical_unit, synonyms, category) VALUES
    ('basophils',    'Basófilos',     '10^9/L', '{"basófilos","basòfils","basophils","Basophile","basophiles","BASO"}', 'Hemograma'),
    ('eosinophils',  'Eosinófilos',   '10^9/L', '{"eosinófilos","eosinòfils","eosinophils","Eosinophile","éosinophiles","EOS"}', 'Hemograma'),
    ('monocytes',    'Monocitos',     '10^9/L', '{"monocitos","monòcits","monocytes","Monozyten","MONO"}', 'Hemograma'),
    ('reticulocytes','Reticulocitos', '10^9/L', '{"reticulocitos","reticulòcits","reticulocytes","Retikulozyten","RETIC"}', 'Hemograma'),
    ('rdw',          'Amplitud de distribución eritrocitaria (RDW)', '%', '{"RDW","ADE","amplitud de distribución eritrocitaria","amplitud de distribució eritrocitària"}', 'Hemograma');
