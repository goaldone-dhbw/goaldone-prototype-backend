--liquibase formatted sql

--changeset goaldone:0010-make-invitations-org-nullable
ALTER TABLE invitations ALTER COLUMN organization_id DROP NOT NULL;
--rollback ALTER TABLE invitations ALTER COLUMN organization_id SET NOT NULL;