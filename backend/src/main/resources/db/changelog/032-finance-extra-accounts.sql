--liquibase formatted sql

--changeset oriol:finance-002
--comment finpension (3a) and the Neon savings Space ("saves") as their own accounts; no demo data
INSERT INTO accounts (name, type, currency) VALUES
    ('finpension', 'investment', 'CHF'),
    ('Neon Saves', 'savings',    'CHF');
--rollback DELETE FROM accounts WHERE name IN ('finpension', 'Neon Saves');
