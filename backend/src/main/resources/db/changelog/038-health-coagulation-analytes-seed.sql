--liquibase formatted sql

--changeset oriol:health-seed-011
--comment Coagulation panel (prothrombin time appears in several languages in the user's reports); semantic matching handles the wording variants
INSERT INTO analytes (code, name, canonical_unit, synonyms, category) VALUES
    ('prothrombin_time', 'Tiempo de protrombina', 's',     '{"tiempo de protrombina","temps de protrombina","temps protrombina","prothrombin time","PT","Prothrombinzeit","Quick"}', 'Coagulación'),
    ('inr',              'INR',                   'ratio', '{"INR","international normalized ratio","índice internacional normalizado","raó normalitzada internacional"}', 'Coagulación'),
    ('aptt',             'Tiempo de tromboplastina parcial (TTPA)', 's', '{"TTPA","aPTT","APTT","tiempo de tromboplastina parcial activado","temps de tromboplastina parcial","partielle Thromboplastinzeit"}', 'Coagulación'),
    ('fibrinogen',      'Fibrinógeno',           'g/L',   '{"fibrinógeno","fibrinogen","fibrinogen","Fibrinogen","fibrinogène"}', 'Coagulación');
