-- liquibase formatted sql

-- changeset goaldone:0012-extend-breaks-model
-- comment: Extend breaks model to support three break types:
--          - ONE_TIME: single break on a specific date
--          - RECURRING: unlimited recurring break (original behavior)
--          - BOUNDED_RECURRING: recurring break within a date range [validFrom, validUntil]
--
--          New columns:
--          - break_type: VARCHAR(20) NOT NULL, enum value (ONE_TIME, RECURRING, BOUNDED_RECURRING)
--          - date: DATE, set only for ONE_TIME breaks
--          - valid_from: DATE, set only for BOUNDED_RECURRING breaks
--          - valid_until: DATE, set only for BOUNDED_RECURRING breaks
--          - organization_id: UUID NOT NULL, denormalized for tenant isolation
--
--          Changes to recurrence columns:
--          - recurrence_type becomes nullable (NULL for ONE_TIME breaks)
--          - recurrence_interval becomes nullable (NULL for ONE_TIME breaks)
--
--          Migration strategy:
--          - All existing breaks are set to RECURRING (preserving original behavior)
--          - Existing users' organizationId is derived from their organization relation

-- Step 1: Drop existing constraints that enforce NOT NULL on recurrence columns
ALTER TABLE breaks DROP CONSTRAINT chk_breaks_recurrence_type;
ALTER TABLE breaks DROP CONSTRAINT chk_breaks_interval;

-- Step 2: Add new columns (initially nullable)
ALTER TABLE breaks ADD COLUMN break_type VARCHAR(20);
ALTER TABLE breaks ADD COLUMN date DATE;
ALTER TABLE breaks ADD COLUMN valid_from DATE;
ALTER TABLE breaks ADD COLUMN valid_until DATE;
ALTER TABLE breaks ADD COLUMN organization_id UUID;

-- Step 3: Populate default values
UPDATE breaks SET break_type = 'RECURRING' WHERE break_type IS NULL;

UPDATE breaks b
SET organization_id = u.organization_id
FROM users u
WHERE b.user_id = u.id AND b.organization_id IS NULL;

-- Step 4: Make the new columns NOT NULL
ALTER TABLE breaks ALTER COLUMN break_type SET NOT NULL;
ALTER TABLE breaks ALTER COLUMN organization_id SET NOT NULL;

-- Step 5: Make recurrence columns nullable (drop their NOT NULL constraint)
ALTER TABLE breaks ALTER COLUMN recurrence_type DROP NOT NULL;
ALTER TABLE breaks ALTER COLUMN recurrence_interval DROP NOT NULL;

-- Step 6: Add new constraints for the extended model
ALTER TABLE breaks ADD CONSTRAINT chk_breaks_break_type CHECK (
    break_type IN ('ONE_TIME', 'RECURRING', 'BOUNDED_RECURRING')
);

ALTER TABLE breaks ADD CONSTRAINT chk_breaks_recurrence CHECK (
    (recurrence_type IS NULL AND recurrence_interval IS NULL)
    OR
    (recurrence_type IS NOT NULL AND recurrence_interval IS NOT NULL AND recurrence_interval >= 1)
);

ALTER TABLE breaks ADD CONSTRAINT chk_breaks_recurrence_type CHECK (
    recurrence_type IS NULL OR recurrence_type IN ('DAILY', 'WEEKLY', 'MONTHLY')
);

ALTER TABLE breaks ADD CONSTRAINT chk_breaks_field_exclusivity CHECK (
    (break_type = 'ONE_TIME' AND date IS NOT NULL AND valid_from IS NULL AND valid_until IS NULL)
    OR
    (break_type = 'RECURRING' AND date IS NULL AND valid_from IS NULL AND valid_until IS NULL)
    OR
    (break_type = 'BOUNDED_RECURRING' AND date IS NULL AND valid_from IS NOT NULL AND valid_until IS NOT NULL)
);

ALTER TABLE breaks ADD CONSTRAINT chk_breaks_valid_date_range CHECK (
    (break_type != 'BOUNDED_RECURRING')
    OR
    (valid_from IS NULL OR valid_until IS NULL OR valid_from <= valid_until)
);

ALTER TABLE breaks ADD CONSTRAINT fk_breaks_organization FOREIGN KEY (organization_id)
    REFERENCES organizations (id) ON DELETE CASCADE;

-- Step 7: Add indexes for tenant isolation and planning
CREATE INDEX idx_breaks_organization_id ON breaks (organization_id);
CREATE INDEX idx_breaks_user_org ON breaks (user_id, organization_id);

-- rollback
-- ALTER TABLE breaks DROP CONSTRAINT fk_breaks_organization;
-- ALTER TABLE breaks DROP CONSTRAINT chk_breaks_break_type;
-- ALTER TABLE breaks DROP CONSTRAINT chk_breaks_recurrence;
-- ALTER TABLE breaks DROP CONSTRAINT chk_breaks_recurrence_type;
-- ALTER TABLE breaks DROP CONSTRAINT chk_breaks_field_exclusivity;
-- ALTER TABLE breaks DROP CONSTRAINT chk_breaks_valid_date_range;
-- DROP INDEX idx_breaks_organization_id;
-- DROP INDEX idx_breaks_user_org;
-- ALTER TABLE breaks DROP COLUMN break_type;
-- ALTER TABLE breaks DROP COLUMN date;
-- ALTER TABLE breaks DROP COLUMN valid_from;
-- ALTER TABLE breaks DROP COLUMN valid_until;
-- ALTER TABLE breaks DROP COLUMN organization_id;
-- ALTER TABLE breaks ALTER COLUMN recurrence_type SET NOT NULL;
-- ALTER TABLE breaks ALTER COLUMN recurrence_interval SET NOT NULL;
-- ALTER TABLE breaks ADD CONSTRAINT chk_breaks_recurrence_type CHECK (recurrence_type IN ('DAILY', 'WEEKLY', 'MONTHLY'));
-- ALTER TABLE breaks ADD CONSTRAINT chk_breaks_interval CHECK (recurrence_interval >= 1);
