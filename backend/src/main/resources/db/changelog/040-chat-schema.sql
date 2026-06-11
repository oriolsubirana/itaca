--liquibase formatted sql

--changeset oriol:chat-001
--comment Esquema del contexto chat: sesiones y mensajes persistidos (una sesión de gym ~1h con muchos turnos)
CREATE TABLE chat_sessions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    titulo      TEXT,
    modo        TEXT        NOT NULL DEFAULT 'general' CHECK (modo IN ('general', 'entreno')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat_messages (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id  BIGINT      NOT NULL REFERENCES chat_sessions (id) ON DELETE CASCADE,
    role        TEXT        NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'TOOL')),
    content     TEXT        NOT NULL,
    tool_calls  JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_session ON chat_messages (session_id, created_at);
--rollback DROP TABLE chat_messages; DROP TABLE chat_sessions;
