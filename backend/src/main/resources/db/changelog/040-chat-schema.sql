--liquibase formatted sql

--changeset oriol:chat-001
--comment Chat context schema: sessions and persisted messages (a gym session lasts ~1h with many turns)
CREATE TABLE chat_sessions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title       TEXT,
    mode        TEXT        NOT NULL DEFAULT 'general' CHECK (mode IN ('general', 'workout')),
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
