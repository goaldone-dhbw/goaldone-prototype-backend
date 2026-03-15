-- liquibase formatted sql

-- changeset goaldone:0005-create-breaks
-- comment: User-configured break blocks that the planning algorithm must respect.
--          start_time / end_time are stored as TIME (wall-clock, no timezone) since
--          breaks are defined relative to the user's local working day, not UTC.
--          Example: "Mittagspause 12:00–13:00 täglich" → DAILY, interval=1.
--          The chk_breaks_time_order constraint ensures end_time > start_time
--          so breaks cannot span midnight (not a valid use case here).
--          updated_at is managed by the application layer (@PreUpdate in JPA).

CREATE TABLE breaks (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    label               VARCHAR(255) NOT NULL,
    start_time          TIME         NOT NULL,
    end_time            TIME         NOT NULL,
    recurrence_type     VARCHAR(10)  NOT NULL,
    recurrence_interval INT          NOT NULL DEFAULT 1,
    user_id             UUID         NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_breaks                 PRIMARY KEY (id),
    CONSTRAINT fk_breaks_user            FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_breaks_recurrence_type CHECK (
        recurrence_type IN ('DAILY', 'WEEKLY', 'MONTHLY')
    ),
    CONSTRAINT chk_breaks_interval CHECK (
        recurrence_interval >= 1
    ),
    CONSTRAINT chk_breaks_time_order CHECK (
        end_time > start_time
    )
);

-- Primary lookup: all breaks for a user (loaded before planning runs)
CREATE INDEX idx_breaks_user_id ON breaks (user_id);

-- rollback DROP TABLE breaks;
