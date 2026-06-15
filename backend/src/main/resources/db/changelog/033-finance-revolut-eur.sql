--liquibase formatted sql

--changeset oriol:finance-003
--comment Revolut is EUR de facto (the CHF pocket is empty), so model it as a single EUR account
UPDATE accounts SET currency = 'EUR' WHERE name = 'Revolut';
--rollback UPDATE accounts SET currency = 'CHF' WHERE name = 'Revolut';
