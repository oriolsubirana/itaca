--liquibase formatted sql

--changeset oriol:health-seed-002
--comment German synonyms (Swiss labs print reports in German) so Zürich reports normalize against the dictionary
UPDATE analytes SET synonyms = synonyms || '{"Calprotectin im Stuhl","fäkales Calprotectin","Calprotectin (Stuhl)"}'::text[] WHERE code = 'fecal_calprotectin';
UPDATE analytes SET synonyms = synonyms || '{"C-reaktives Protein","CRP hochsensitiv"}'::text[] WHERE code = 'crp';
UPDATE analytes SET synonyms = synonyms || '{"Blutsenkung","Blutsenkungsgeschwindigkeit","Blutkörperchensenkung"}'::text[] WHERE code = 'esr';
UPDATE analytes SET synonyms = synonyms || '{"Eisen","Serumeisen"}'::text[] WHERE code = 'iron';
UPDATE analytes SET synonyms = synonyms || '{"Transferrinsättigung"}'::text[] WHERE code = 'transferrin_saturation';
UPDATE analytes SET synonyms = synonyms || '{"Vitamin B12","Cobalamin","Holotranscobalamin"}'::text[] WHERE code = 'vitamin_b12';
UPDATE analytes SET synonyms = synonyms || '{"Folsäure","Folat"}'::text[] WHERE code = 'folate';
UPDATE analytes SET synonyms = synonyms || '{"Vitamin D","25-OH-Vitamin D","25-Hydroxy-Vitamin D"}'::text[] WHERE code = 'vitamin_d';
UPDATE analytes SET synonyms = synonyms || '{"Hämoglobin"}'::text[] WHERE code = 'hemoglobin';
UPDATE analytes SET synonyms = synonyms || '{"Hämatokrit","Hkt"}'::text[] WHERE code = 'hematocrit';
UPDATE analytes SET synonyms = synonyms || '{"mittleres korpuskuläres Volumen"}'::text[] WHERE code = 'mcv';
UPDATE analytes SET synonyms = synonyms || '{"Leukozyten","weisse Blutkörperchen"}'::text[] WHERE code = 'leukocytes';
UPDATE analytes SET synonyms = synonyms || '{"Neutrophile","Neutrophile Granulozyten"}'::text[] WHERE code = 'neutrophils';
UPDATE analytes SET synonyms = synonyms || '{"Lymphozyten"}'::text[] WHERE code = 'lymphocytes';
UPDATE analytes SET synonyms = synonyms || '{"Thrombozyten","Blutplättchen"}'::text[] WHERE code = 'platelets';
UPDATE analytes SET synonyms = synonyms || '{"Gesamteiweiss","Gesamtprotein","Totalprotein"}'::text[] WHERE code = 'total_protein';
UPDATE analytes SET synonyms = synonyms || '{"Alanin-Aminotransferase"}'::text[] WHERE code = 'alt';
UPDATE analytes SET synonyms = synonyms || '{"Aspartat-Aminotransferase"}'::text[] WHERE code = 'ast';
UPDATE analytes SET synonyms = synonyms || '{"Gamma-Glutamyltransferase"}'::text[] WHERE code = 'ggt';
UPDATE analytes SET synonyms = synonyms || '{"alkalische Phosphatase"}'::text[] WHERE code = 'alkaline_phosphatase';
UPDATE analytes SET synonyms = synonyms || '{"Bilirubin gesamt","Gesamtbilirubin","Bilirubin total"}'::text[] WHERE code = 'total_bilirubin';
UPDATE analytes SET synonyms = synonyms || '{"Kreatinin"}'::text[] WHERE code = 'creatinine';
UPDATE analytes SET synonyms = synonyms || '{"Harnstoff"}'::text[] WHERE code = 'urea';
UPDATE analytes SET synonyms = synonyms || '{"glomeruläre Filtrationsrate"}'::text[] WHERE code = 'egfr';
UPDATE analytes SET synonyms = synonyms || '{"Natrium"}'::text[] WHERE code = 'sodium';
UPDATE analytes SET synonyms = synonyms || '{"Kalium"}'::text[] WHERE code = 'potassium';
UPDATE analytes SET synonyms = synonyms || '{"Glukose","Blutzucker"}'::text[] WHERE code = 'glucose';
UPDATE analytes SET synonyms = synonyms || '{"Thyreotropin"}'::text[] WHERE code = 'tsh';

--changeset oriol:health-seed-003
--comment Thyroid and B6 analytes that appear in the user's Swiss panels
INSERT INTO analytes (code, name, canonical_unit, synonyms) VALUES
    ('ft4',        'Tiroxina libre (FT4)',         'pmol/L', '{"FT4","T4 libre","tiroxina libre","freies T4","freies Thyroxin","free T4"}'),
    ('ft3',        'Triyodotironina libre (FT3)',  'pmol/L', '{"FT3","T3 libre","triyodotironina libre","freies T3","freies Trijodthyronin","free T3"}'),
    ('vitamin_b6', 'Vitamina B6',                  'µg/L',   '{"vitamina B6","Vitamin B6","piridoxina","Pyridoxin","B6"}');
