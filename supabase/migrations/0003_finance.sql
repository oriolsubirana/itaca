-- Ítaca · Módulo 3: Finanzas
-- Cuentas, snapshots de saldo y transacciones. Moneda base CHF con soporte EUR.

create table if not exists accounts (
  id         uuid primary key default gen_random_uuid(),
  nombre     text not null unique,
  tipo       text,                      -- corriente, ahorro, inversión, ...
  moneda     text not null default 'CHF' check (moneda in ('CHF','EUR')),
  created_at timestamptz not null default now()
);

-- Foto del saldo de una cuenta en una fecha (para patrimonio neto)
create table if not exists balance_snapshots (
  id         uuid primary key default gen_random_uuid(),
  account_id uuid not null references accounts(id) on delete cascade,
  fecha      date not null default current_date,
  saldo      numeric(14,2) not null,
  created_at timestamptz not null default now(),
  unique (account_id, fecha)
);

-- Transacciones importadas (CSV) o manuales
create table if not exists transactions (
  id          uuid primary key default gen_random_uuid(),
  account_id  uuid not null references accounts(id) on delete cascade,
  fecha       date not null,
  importe     numeric(14,2) not null,   -- negativo = gasto, positivo = ingreso
  descripcion text,
  categoria   text,
  created_at  timestamptz not null default now()
);

create index if not exists idx_snapshots_account on balance_snapshots (account_id, fecha desc);
create index if not exists idx_tx_account on transactions (account_id, fecha desc);
create index if not exists idx_tx_categoria on transactions (categoria);
