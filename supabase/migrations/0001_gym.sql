-- Ítaca · Módulo 1: Entrenamiento (Gym)
-- Rotación Push/Pull/Leg, registro de series por sesión.

create extension if not exists "pgcrypto";

-- Ejercicios disponibles
create table if not exists exercises (
  id             uuid primary key default gen_random_uuid(),
  nombre         text not null unique,
  grupo_muscular text,
  created_at     timestamptz not null default now()
);

-- Rutinas (Push/Pull/Leg) con ejercicios ordenados
create table if not exists routines (
  id           uuid primary key default gen_random_uuid(),
  nombre       text not null unique,
  exercise_ids uuid[] not null default '{}',  -- orden de ejecución
  created_at   timestamptz not null default now()
);

-- Sesiones de entrenamiento
create table if not exists workouts (
  id         uuid primary key default gen_random_uuid(),
  fecha      date not null default current_date,
  routine_id uuid references routines(id) on delete set null,
  notas      text,
  completado boolean not null default false,
  created_at timestamptz not null default now()
);

-- Series registradas dentro de una sesión
create table if not exists sets (
  id          uuid primary key default gen_random_uuid(),
  workout_id  uuid not null references workouts(id) on delete cascade,
  exercise_id uuid not null references exercises(id) on delete restrict,
  peso        numeric(6,2) not null,
  reps        integer not null,
  orden       integer not null default 0,
  rpe         numeric(3,1),
  created_at  timestamptz not null default now()
);

create index if not exists idx_workouts_fecha on workouts (fecha desc);
create index if not exists idx_sets_workout on sets (workout_id);
create index if not exists idx_sets_exercise on sets (exercise_id);
