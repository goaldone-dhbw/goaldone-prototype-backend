-- liquibase formatted sql

-- changeset goaldone:0004-create-tasks
-- comment: Core task entity. Key design decisions:
--
--          owner_id is intentionally separate from the authenticated user's id.
--          Currently always set to the creating user, but decoupled so that
--          a future Team entity can be set as owner without a schema change.
--
--          parent_task_id enables automatic task splitting: when a task's
--          estimated_duration_minutes exceeds one working day (480 min), the
--          planning algorithm splits it into child tasks that reference the original.
--
--          recurrence_type + recurrence_interval implement the MVP recurrence model
--          (DAILY/WEEKLY/MONTHLY + every N units). Nullable as a pair — both must
--          be set together or both NULL (enforced by chk_tasks_recurrence).
--
--          updated_at is managed by the application layer (@PreUpdate in JPA).

CREATE TABLE tasks (
    id                          UUID         NOT NULL DEFAULT gen_random_uuid(),
    title                       VARCHAR(255) NOT NULL,
    description                 TEXT,
    status                      VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    cognitive_load              VARCHAR(10)  NOT NULL,
    estimated_duration_minutes  INT          NOT NULL,
    deadline                    DATE,
    recurrence_type             VARCHAR(10),
    recurrence_interval         INT,
    parent_task_id              UUID,
    owner_id                    UUID         NOT NULL,
    organization_id             UUID         NOT NULL,
    completed_at                TIMESTAMP,
    created_at                  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_tasks                  PRIMARY KEY (id),
    CONSTRAINT fk_tasks_owner            FOREIGN KEY (owner_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_organization     FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_parent           FOREIGN KEY (parent_task_id)
        REFERENCES tasks (id) ON DELETE SET NULL,

    CONSTRAINT chk_tasks_status CHECK (
        status IN ('OPEN', 'IN_PROGRESS', 'DONE')
    ),
    CONSTRAINT chk_tasks_cognitive_load CHECK (
        cognitive_load IN ('LOW', 'MEDIUM', 'HIGH')
    ),
    CONSTRAINT chk_tasks_duration CHECK (
        estimated_duration_minutes >= 1
    ),
    CONSTRAINT chk_tasks_recurrence CHECK (
        (recurrence_type IS NULL AND recurrence_interval IS NULL)
        OR
        (recurrence_type IS NOT NULL AND recurrence_interval IS NOT NULL AND recurrence_interval >= 1)
    ),
    CONSTRAINT chk_tasks_recurrence_type CHECK (
        recurrence_type IS NULL OR recurrence_type IN ('DAILY', 'WEEKLY', 'MONTHLY')
    ),
    CONSTRAINT chk_tasks_completed CHECK (
        (status = 'DONE' AND completed_at IS NOT NULL)
        OR
        (status != 'DONE' AND completed_at IS NULL)
    )
);

-- Core lookup: all tasks for a user (main list endpoint)
CREATE INDEX idx_tasks_owner_id         ON tasks (owner_id);

-- Tenant isolation enforcement
CREATE INDEX idx_tasks_organization_id  ON tasks (organization_id);

-- Planning algorithm: find tasks by deadline and status
CREATE INDEX idx_tasks_deadline         ON tasks (deadline);
CREATE INDEX idx_tasks_status           ON tasks (status);

-- Composite: the planning algorithm's primary query
-- "Give me all OPEN tasks for this user, ordered by deadline"
CREATE INDEX idx_tasks_planning ON tasks (owner_id, status, deadline);

-- Task splitting: find all sub-tasks of a parent
CREATE INDEX idx_tasks_parent_task_id ON tasks (parent_task_id);

-- rollback DROP TABLE tasks;
