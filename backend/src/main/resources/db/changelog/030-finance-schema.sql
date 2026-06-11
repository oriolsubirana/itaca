--liquibase formatted sql

--changeset oriol:finance-001
--comment Esquema del contexto finance: cuentas, snapshots de saldo y transacciones
CREATE TABLE accounts (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  TEXT    NOT NULL UNIQUE,
    tipo    TEXT    NOT NULL CHECK (tipo IN ('corriente', 'ahorro', 'inversion')),
    moneda  CHAR(3) NOT NULL CHECK (moneda IN ('CHF', 'EUR'))
);

CREATE TABLE balance_snapshots (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  BIGINT         NOT NULL REFERENCES accounts (id),
    fecha       DATE           NOT NULL,
    saldo       NUMERIC(14,2)  NOT NULL,
    UNIQUE (account_id, fecha)
);

CREATE TABLE transactions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id   BIGINT         NOT NULL REFERENCES accounts (id),
    fecha        DATE           NOT NULL,
    importe      NUMERIC(12,2)  NOT NULL,
    descripcion  TEXT           NOT NULL,
    categoria    TEXT
);

CREATE INDEX idx_transactions_account_fecha ON transactions (account_id, fecha DESC);
--rollback DROP TABLE transactions; DROP TABLE balance_snapshots; DROP TABLE accounts;
