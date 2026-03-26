-- liquibase formatted sql

-- changeset goaldone:0011-add-start-date-to-tasks-and-flags-to-schedule-entries
-- comment: Add start_date to tasks and is_completed/is_pinned to schedule_entries

ALTER TABLE tasks ADD COLUMN start_date DATE NULL;
ALTER TABLE schedule_entries ADD COLUMN is_completed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE schedule_entries ADD COLUMN is_pinned BOOLEAN NOT NULL DEFAULT FALSE;

-- rollback ALTER TABLE tasks DROP COLUMN start_date;
-- rollback ALTER TABLE schedule_entries DROP COLUMN is_completed;
-- rollback ALTER TABLE schedule_entries DROP COLUMN is_pinned;
