--liquibase formatted sql

--changeset oriol:finance-001
--comment Finance context schema: accounts, balance snapshots and transactions
CREATE TABLE accounts (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name      TEXT    NOT NULL UNIQUE,
    type      TEXT    NOT NULL CHECK (type IN ('checking', 'savings', 'investment')),
    currency  CHAR(3) NOT NULL CHECK (currency IN ('CHF', 'EUR'))
);

CREATE TABLE balance_snapshots (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  BIGINT         NOT NULL REFERENCES accounts (id),
    date        DATE           NOT NULL,
    balance     NUMERIC(14,2)  NOT NULL,
    UNIQUE (account_id, date)
);

CREATE TABLE transactions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id   BIGINT         NOT NULL REFERENCES accounts (id),
    date         DATE           NOT NULL,
    amount       NUMERIC(12,2)  NOT NULL,
    description  TEXT           NOT NULL,
    category     TEXT
);

CREATE INDEX idx_transactions_account_date ON transactions (account_id, date DESC);
--rollback DROP TABLE transactions; DROP TABLE balance_snapshots; DROP TABLE accounts;
