-- liquibase formatted sql

-- changeset goaldone:0006-create-schedule-entries
-- comment: Stores the output of the planning algorithm — one row per time block per day.
--          Each entry is either a TASK block or a BREAK block (entry_type discriminator).
--          task_id and break_id are mutually exclusive: exactly one must be set,
--          enforced by chk_schedule_entry_type_consistency.
--
--          generated_at records when the planning run that produced this entry occurred.
--          When the algorithm re-runs for a date range, the existing entries for that
--          range are deleted first, then new ones inserted — so the table always
--          reflects the latest plan.
--
--          organization_id is denormalized here (it could be derived via user→org)
--          to make tenant-scoped schedule queries faster without a join.

CREATE TABLE schedule_entries (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    organization_id UUID        NOT NULL,
    task_id         UUID,
    break_id        UUID,
    entry_date      DATE        NOT NULL,
    start_time      TIME        NOT NULL,
    end_time        TIME        NOT NULL,
    entry_type      VARCHAR(10) NOT NULL,
    generated_at    TIMESTAMP   NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_schedule_entries              PRIMARY KEY (id),
    CONSTRAINT fk_schedule_entries_user         FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_entries_organization FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_entries_task         FOREIGN KEY (task_id)
        REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_entries_break        FOREIGN KEY (break_id)
        REFERENCES breaks (id) ON DELETE CASCADE,

    CONSTRAINT chk_schedule_entry_type CHECK (
        entry_type IN ('TASK', 'BREAK')
    ),
    CONSTRAINT chk_schedule_entry_time_order CHECK (
        end_time > start_time
    ),
    -- Exactly one of task_id / break_id must be set, matching entry_type
    CONSTRAINT chk_schedule_entry_type_consistency CHECK (
        (entry_type = 'TASK'  AND task_id  IS NOT NULL AND break_id IS NULL)
        OR
        (entry_type = 'BREAK' AND break_id IS NOT NULL AND task_id  IS NULL)
    ),
    -- No two entries for the same user can overlap on the same day
    -- (partial overlap is prevented in the application layer before insert;
    --  this unique constraint prevents exact duplicate time blocks)
    CONSTRAINT uq_schedule_entry_slot UNIQUE (user_id, entry_date, start_time)
);

-- Primary query: "show me the schedule for user X between date A and date B"
CREATE INDEX idx_schedule_entries_user_date ON schedule_entries (user_id, entry_date);

-- Planning re-run: delete all entries in a date range for a user before regenerating
CREATE INDEX idx_schedule_entries_date_range ON schedule_entries (user_id, entry_date, end_time);

-- Task-schedule join: "which time slots has this task been scheduled into?"
CREATE INDEX idx_schedule_entries_task_id ON schedule_entries (task_id);

-- Tenant scoping
CREATE INDEX idx_schedule_entries_organization_id ON schedule_entries (organization_id);

-- rollback DROP TABLE schedule_entries;
