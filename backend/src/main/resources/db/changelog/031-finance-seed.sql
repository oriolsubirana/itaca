--liquibase formatted sql

--changeset oriol:finance-seed-001
--comment Accounts: Neon and Revolut in CHF, MyInvestor in EUR
INSERT INTO accounts (name, type, currency) VALUES
    ('Neon',       'checking',    'CHF'),
    ('Revolut',    'checking',    'CHF'),
    ('MyInvestor', 'investment',  'EUR');
