--liquibase formatted sql

--changeset oriol:tasks-001
--comment Personal to-do list; open/done tasks with an optional deadline, added by chat or by hand
CREATE TABLE tasks (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title      TEXT        NOT NULL,
    notes      TEXT,
    due_date   DATE,
    done       BOOLEAN     NOT NULL DEFAULT FALSE,
    done_at    TIMESTAMPTZ,
    source     TEXT        NOT NULL DEFAULT 'manual' CHECK (source IN ('manual', 'chat', 'email')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tasks_done ON tasks (done, due_date);
--rollback DROP TABLE tasks;
