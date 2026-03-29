-- liquibase formatted sql

-- changeset johannes:0013-create-recurring-templates
-- comment: Create recurring templates and exceptions tables for recurring tasks.

CREATE TABLE recurring_templates (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    title                VARCHAR(255) NOT NULL,
    description          TEXT,
    cognitive_load       VARCHAR(20)  NOT NULL,
    duration_minutes     INT          NOT NULL,
    recurrence_type      VARCHAR(20)  NOT NULL,
    recurrence_interval  INT          NOT NULL DEFAULT 1,
    preferred_start_time TIME,
    owner_id             UUID         NOT NULL,
    organization_id      UUID         NOT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_recurring_templates PRIMARY KEY (id),
    CONSTRAINT fk_recurring_templates_owner FOREIGN KEY (owner_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_templates_org FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE INDEX idx_recurring_templates_owner_org ON recurring_templates(owner_id, organization_id);
CREATE INDEX idx_recurring_templates_org ON recurring_templates(organization_id);

CREATE TABLE recurring_exceptions (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    template_id      UUID         NOT NULL,
    occurrence_date  DATE         NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    new_date         DATE,
    new_start_time   TIME,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_recurring_exceptions PRIMARY KEY (id),
    CONSTRAINT fk_recurring_exceptions_template FOREIGN KEY (template_id)
        REFERENCES recurring_templates (id) ON DELETE CASCADE,
    CONSTRAINT uq_template_occurrence UNIQUE (template_id, occurrence_date)
);

CREATE INDEX idx_recurring_exceptions_template ON recurring_exceptions(template_id);
CREATE INDEX idx_recurring_exceptions_date_range ON recurring_exceptions(template_id, occurrence_date);

-- rollback DROP TABLE recurring_exceptions;
-- rollback DROP TABLE recurring_templates;
