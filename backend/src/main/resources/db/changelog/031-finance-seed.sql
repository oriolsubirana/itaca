--liquibase formatted sql

--changeset oriol:finance-seed-001
--comment Cuentas: Neon y Revolut en CHF, MyInvestor y Sabadell en EUR
INSERT INTO accounts (nombre, tipo, moneda) VALUES
    ('Neon',       'corriente',  'CHF'),
    ('Revolut',    'corriente',  'CHF'),
    ('MyInvestor', 'inversion',  'EUR'),
    ('Sabadell',   'corriente',  'EUR');
