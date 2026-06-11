-- Ítaca · Módulo 2: Salud — EII (colitis ulcerosa)
-- Diario de síntomas, comidas, brotes y analíticas.

-- Registro diario de síntomas (una entrada por día)
create table if not exists diary_entries (
  id               uuid primary key default gen_random_uuid(),
  fecha            date not null unique default current_date,
  bristol          integer check (bristol between 1 and 7),
  dolor            integer check (dolor between 0 and 10),
  urgencia         integer check (urgencia between 0 and 10),
  sangre           boolean,
  num_deposiciones integer,
  estres           integer check (estres between 0 and 10),
  notas            text,
  created_at       timestamptz not null default now()
);

-- Comidas asociadas a un día
create table if not exists meals (
  id              uuid primary key default gen_random_uuid(),
  diary_entry_id  uuid not null references diary_entries(id) on delete cascade,
  descripcion     text not null,
  tags            text[] not null default '{}',  -- lácteos, gluten, alcohol, picante, fodmap_alto
  created_at      timestamptz not null default now()
);

-- Brotes (flares)
create table if not exists flares (
  id           uuid primary key default gen_random_uuid(),
  fecha_inicio date not null,
  fecha_fin    date,
  severidad    integer check (severidad between 0 and 10),
  notas        text,
  created_at   timestamptz not null default now()
);

-- Informes de analítica (un PDF / extracción)
create table if not exists lab_reports (
  id          uuid primary key default gen_random_uuid(),
  fecha       date not null,
  laboratorio text,
  created_at  timestamptz not null default now()
);

-- Resultados individuales normalizados
create table if not exists lab_results (
  id            uuid primary key default gen_random_uuid(),
  lab_report_id uuid not null references lab_reports(id) on delete cascade,
  analito       text not null,        -- nombre canónico (ver lab_analytes)
  valor         numeric,
  unidad        text,
  ref_min       numeric,
  ref_max       numeric,
  created_at    timestamptz not null default now()
);

-- Diccionario de analitos comunes en EII con sinónimos (ES/CH) para normalización
create table if not exists lab_analytes (
  id        uuid primary key default gen_random_uuid(),
  canonico  text not null unique,
  unidad    text,
  ref_min   numeric,
  ref_max   numeric,
  sinonimos text[] not null default '{}'
);

create index if not exists idx_diary_fecha on diary_entries (fecha desc);
create index if not exists idx_meals_diary on meals (diary_entry_id);
create index if not exists idx_lab_results_report on lab_results (lab_report_id);
create index if not exists idx_lab_results_analito on lab_results (analito);
create index if not exists idx_flares_inicio on flares (fecha_inicio desc);
