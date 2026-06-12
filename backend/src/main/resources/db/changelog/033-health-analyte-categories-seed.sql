--liquibase formatted sql

--changeset oriol:health-seed-006
--comment Lab panel per analyte, so the chart selector groups them by theme (display labels are UI text, Spanish)
UPDATE analytes SET category = 'Inflamación / EII' WHERE code IN ('fecal_calprotectin', 'crp', 'esr');
UPDATE analytes SET category = 'Hemograma' WHERE code IN ('hemoglobin', 'hematocrit', 'mcv', 'leukocytes', 'neutrophils', 'lymphocytes', 'platelets', 'erythrocytes');
UPDATE analytes SET category = 'Hierro' WHERE code IN ('iron', 'transferrin', 'transferrin_saturation', 'ferritin');
UPDATE analytes SET category = 'Vitaminas' WHERE code IN ('vitamin_b12', 'folate', 'vitamin_d', 'vitamin_b6');
UPDATE analytes SET category = 'Hígado' WHERE code IN ('alt', 'ast', 'ggt', 'alkaline_phosphatase', 'total_bilirubin', 'albumin', 'total_protein');
UPDATE analytes SET category = 'Riñón' WHERE code IN ('creatinine', 'urea', 'egfr');
UPDATE analytes SET category = 'Electrolitos' WHERE code IN ('sodium', 'potassium', 'magnesium');
UPDATE analytes SET category = 'Tiroides' WHERE code IN ('tsh', 'ft4', 'ft3');
UPDATE analytes SET category = 'Metabólico' WHERE code IN ('glucose');
