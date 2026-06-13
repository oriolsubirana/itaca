--liquibase formatted sql

--changeset oriol:health-seed-008
--comment Remaining common analytes seen in the user's reports: MCH/MCHC, pancreatic enzymes, lipid panel + HbA1c, and a fertility (semen) panel
INSERT INTO analytes (code, name, canonical_unit, synonyms, category) VALUES
    ('mch',  'Hemoglobina corpuscular media (HCM)', 'pg',    '{"HCM","MCH","hemoglobina corpuscular media","hemoglobina corpuscular mitja","hemoglobina corpuscular mitjana","mean corpuscular hemoglobin","mittleres korpuskuläres Hämoglobin","MCH-Wert"}', 'Hemograma'),
    ('mchc', 'Concentración de HCM (CHCM)',         'g/dL',  '{"CHCM","MCHC","concentración de hemoglobina corpuscular media","concentració d''hemoglobina corpuscular mitjana","mean corpuscular hemoglobin concentration","mittlere korpuskuläre Hämoglobinkonzentration"}', 'Hemograma'),
    ('lipase',   'Lipasa',  'U/L', '{"lipasa","lipase","Lipase"}', 'Páncreas'),
    ('amylase',  'Amilasa', 'U/L', '{"amilasa","amilase","amylase","Amylase","amilasa pancreática"}', 'Páncreas'),
    ('total_cholesterol', 'Colesterol total', 'mg/dL', '{"colesterol total","colesterol","cholesterol","total cholesterol","Cholesterin","Gesamtcholesterin","colesterol total en sang"}', 'Lípidos'),
    ('hdl_cholesterol',   'Colesterol HDL',   'mg/dL', '{"HDL","colesterol HDL","HDL-colesterol","HDL cholesterol","HDL-Cholesterin"}', 'Lípidos'),
    ('ldl_cholesterol',   'Colesterol LDL',   'mg/dL', '{"LDL","colesterol LDL","LDL-colesterol","LDL cholesterol","LDL-Cholesterin"}', 'Lípidos'),
    ('triglycerides',     'Triglicéridos',    'mg/dL', '{"triglicéridos","triglicèrids","triglycerides","Triglyzeride","TG"}', 'Lípidos'),
    ('hba1c', 'Hemoglobina glicada (HbA1c)', '%', '{"HbA1c","hemoglobina glicada","hemoglobina glicosilada","hemoglobina glucada","glycated hemoglobin","glykiertes Hämoglobin","A1c"}', 'Metabólico'),
    ('semen_concentration', 'Concentración espermática', 'mill/mL', '{"concentración espermática","concentració espermàtica","concentración de espermatozoides","sperm concentration","Spermienkonzentration","concentración de esperma"}', 'Fertilidad'),
    ('semen_volume',        'Volumen seminal',           'mL',      '{"volumen seminal","volum seminal","semen volume","Ejakulatvolumen","volumen del eyaculado"}', 'Fertilidad'),
    ('semen_motility',      'Motilidad espermática',     '%',       '{"motilidad espermática","motilitat espermàtica","sperm motility","Motilität","movilidad espermática","motilidad total"}', 'Fertilidad'),
    ('semen_morphology',    'Morfología espermática',    '%',       '{"morfología espermática","morfologia espermàtica","sperm morphology","Morphologie","formas normales"}', 'Fertilidad'),
    ('semen_count',         'Recuento espermático total','mill',    '{"recuento espermático total","recompte espermàtic total","total sperm count","Gesamtspermienzahl","número total de espermatozoides"}', 'Fertilidad');
