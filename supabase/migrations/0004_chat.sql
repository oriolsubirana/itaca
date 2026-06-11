-- Ítaca · Chat con Claude (lectura y escritura)
-- Mantiene el historial de la sesión activa en DB. Una sesión de gym dura ~1h.

create table if not exists chat_sessions (
  id         uuid primary key default gen_random_uuid(),
  modo       text not null default 'general',  -- 'general' | 'entreno'
  workout_id uuid references workouts(id) on delete set null,
  titulo     text,
  activa     boolean not null default true,
  created_at timestamptz not null default now(),
  ended_at   timestamptz
);

-- Mensajes en formato compatible con la Anthropic Messages API.
-- content es jsonb para preservar bloques (text, tool_use, tool_result).
create table if not exists chat_messages (
  id         uuid primary key default gen_random_uuid(),
  session_id uuid not null references chat_sessions(id) on delete cascade,
  role       text not null check (role in ('user','assistant')),
  content    jsonb not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_chat_messages_session on chat_messages (session_id, created_at);
create index if not exists idx_chat_sessions_activa on chat_sessions (activa, created_at desc);
