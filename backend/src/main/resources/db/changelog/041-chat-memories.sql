--liquibase formatted sql

--changeset oriol:chat-002
--comment Persistent user memory: durable personal facts the chat must remember across sessions
CREATE TABLE chat_memories (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE chat_memories;
