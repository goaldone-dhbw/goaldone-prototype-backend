-- liquibase formatted sql

-- changeset goaldone:0009-fix-users-org-cascade
-- comment: Change FK constraint for users.organization_id from ON DELETE SET NULL to ON DELETE CASCADE
--          This ensures that when an organization is deleted, all its members are also deleted.

ALTER TABLE users DROP CONSTRAINT fk_users_organization;

ALTER TABLE users ADD CONSTRAINT fk_users_organization 
    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE;

-- rollback ALTER TABLE users DROP CONSTRAINT fk_users_organization;
-- rollback ALTER TABLE users ADD CONSTRAINT fk_users_organization FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL;
