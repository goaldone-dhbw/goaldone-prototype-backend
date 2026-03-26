-- liquibase formatted sql

-- changeset goaldone:0011-create-working-hours
-- comment: Wöchentliche Arbeitszeit-Vorlage für Nutzer.
--          start_time / end_time sind TIME (wall-clock) da sie
--          relativ zum lokalen Tag des Nutzers definiert sind.

CREATE TABLE working_hour_entries (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    day_of_week   VARCHAR(10)  NOT NULL,
    is_work_day   BOOLEAN      NOT NULL,
    start_time    TIME,
    end_time      TIME,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_working_hour_entries        PRIMARY KEY (id),
    CONSTRAINT fk_working_hour_entries_user   FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_working_hours_user_day      UNIQUE (user_id, day_of_week),
    CONSTRAINT chk_working_hours_day          CHECK (
        day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    )
);

-- Index for lookup
CREATE INDEX idx_working_hour_entries_user_id ON working_hour_entries (user_id);

-- rollback DROP TABLE working_hour_entries;
