-- changeset johannes:0013-create-recurring-templates
-- Create recurring_templates table
CREATE TABLE recurring_templates (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title                VARCHAR(255) NOT NULL,
    description          TEXT,
    cognitive_load       VARCHAR(20) NOT NULL,
    duration_minutes     INT NOT NULL,
    recurrence_type      VARCHAR(20) NOT NULL,
    recurrence_interval  INT NOT NULL DEFAULT 1,
    preferred_start_time TIME,
    owner_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id      UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP NOT NULL DEFAULT now()
);

-- Create indexes for efficient lookups
CREATE INDEX idx_recurring_templates_owner_org ON recurring_templates(owner_id, organization_id);
CREATE INDEX idx_recurring_templates_org ON recurring_templates(organization_id);

-- Create recurring_exceptions table
CREATE TABLE recurring_exceptions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id      UUID NOT NULL REFERENCES recurring_templates(id) ON DELETE CASCADE,
    occurrence_date  DATE NOT NULL,
    type             VARCHAR(20) NOT NULL,
    new_date         DATE,
    new_start_time   TIME,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_template_occurrence UNIQUE (template_id, occurrence_date)
);

-- Create indexes for efficient lookups
CREATE INDEX idx_recurring_exceptions_template ON recurring_exceptions(template_id);
CREATE INDEX idx_recurring_exceptions_date_range ON recurring_exceptions(template_id, occurrence_date);

-- rollback DROP TABLE IF EXISTS recurring_exceptions CASCADE;
-- rollback DROP TABLE IF EXISTS recurring_templates CASCADE;
