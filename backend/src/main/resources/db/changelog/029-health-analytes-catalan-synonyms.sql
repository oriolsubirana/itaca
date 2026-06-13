--liquibase formatted sql

--changeset oriol:health-seed-004
--comment Catalan synonyms (Parc Taulí and other Catalan labs print reports in Catalan) so those reports normalize
UPDATE analytes SET synonyms = synonyms || '{"calprotectina en femta","calprotectina fecal"}'::text[] WHERE code = 'fecal_calprotectin';
UPDATE analytes SET synonyms = synonyms || '{"proteïna C reactiva","PCR"}'::text[] WHERE code = 'crp';
UPDATE analytes SET synonyms = synonyms || '{"velocitat de sedimentació globular","velocitat de sedimentació"}'::text[] WHERE code = 'esr';
UPDATE analytes SET synonyms = synonyms || '{"ferro","ferro sèric"}'::text[] WHERE code = 'iron';
UPDATE analytes SET synonyms = synonyms || '{"saturació de transferrina","índex de saturació de transferrina"}'::text[] WHERE code = 'transferrin_saturation';
UPDATE analytes SET synonyms = synonyms || '{"vitamina B12","cobalamina"}'::text[] WHERE code = 'vitamin_b12';
UPDATE analytes SET synonyms = synonyms || '{"àcid fòlic","folat"}'::text[] WHERE code = 'folate';
UPDATE analytes SET synonyms = synonyms || '{"vitamina D","25-OH-vitamina D"}'::text[] WHERE code = 'vitamin_d';
UPDATE analytes SET synonyms = synonyms || '{"hemoglobina"}'::text[] WHERE code = 'hemoglobin';
UPDATE analytes SET synonyms = synonyms || '{"hematòcrit"}'::text[] WHERE code = 'hematocrit';
UPDATE analytes SET synonyms = synonyms || '{"volum corpuscular mig","VCM"}'::text[] WHERE code = 'mcv';
UPDATE analytes SET synonyms = synonyms || '{"leucòcits"}'::text[] WHERE code = 'leukocytes';
UPDATE analytes SET synonyms = synonyms || '{"neutròfils"}'::text[] WHERE code = 'neutrophils';
UPDATE analytes SET synonyms = synonyms || '{"limfòcits"}'::text[] WHERE code = 'lymphocytes';
UPDATE analytes SET synonyms = synonyms || '{"plaquetes"}'::text[] WHERE code = 'platelets';
UPDATE analytes SET synonyms = synonyms || '{"albúmina"}'::text[] WHERE code = 'albumin';
UPDATE analytes SET synonyms = synonyms || '{"proteïnes totals"}'::text[] WHERE code = 'total_protein';
UPDATE analytes SET synonyms = synonyms || '{"alanina aminotransferasa"}'::text[] WHERE code = 'alt';
UPDATE analytes SET synonyms = synonyms || '{"aspartat aminotransferasa"}'::text[] WHERE code = 'ast';
UPDATE analytes SET synonyms = synonyms || '{"gamma-glutamil transferasa"}'::text[] WHERE code = 'ggt';
UPDATE analytes SET synonyms = synonyms || '{"fosfatasa alcalina"}'::text[] WHERE code = 'alkaline_phosphatase';
UPDATE analytes SET synonyms = synonyms || '{"bilirubina total"}'::text[] WHERE code = 'total_bilirubin';
UPDATE analytes SET synonyms = synonyms || '{"creatinina"}'::text[] WHERE code = 'creatinine';
UPDATE analytes SET synonyms = synonyms || '{"urea"}'::text[] WHERE code = 'urea';
UPDATE analytes SET synonyms = synonyms || '{"filtrat glomerular estimat","filtrat glomerular"}'::text[] WHERE code = 'egfr';
UPDATE analytes SET synonyms = synonyms || '{"sodi"}'::text[] WHERE code = 'sodium';
UPDATE analytes SET synonyms = synonyms || '{"potassi"}'::text[] WHERE code = 'potassium';
UPDATE analytes SET synonyms = synonyms || '{"magnesi"}'::text[] WHERE code = 'magnesium';
UPDATE analytes SET synonyms = synonyms || '{"glucosa","glucèmia"}'::text[] WHERE code = 'glucose';
UPDATE analytes SET synonyms = synonyms || '{"hormona estimulant de la tiroide","tirotropina"}'::text[] WHERE code = 'tsh';
UPDATE analytes SET synonyms = synonyms || '{"tiroxina lliure","T4 lliure"}'::text[] WHERE code = 'ft4';
UPDATE analytes SET synonyms = synonyms || '{"triiodotironina lliure","T3 lliure"}'::text[] WHERE code = 'ft3';
UPDATE analytes SET synonyms = synonyms || '{"vitamina B6","piridoxina"}'::text[] WHERE code = 'vitamin_b6';

--changeset oriol:health-seed-005
--comment Red blood cell count, printed on Catalan/Spanish CBCs (Hematies) and missing from the dictionary
INSERT INTO analytes (code, name, canonical_unit, synonyms) VALUES
    ('erythrocytes', 'Hematíes', '10^12/L', '{"hematíes","hematies","eritrocitos","eritròcits","glóbulos rojos","glòbuls vermells","red blood cells","RBC","erythrocytes","Erythrozyten","hématies","érythrocytes"}');
