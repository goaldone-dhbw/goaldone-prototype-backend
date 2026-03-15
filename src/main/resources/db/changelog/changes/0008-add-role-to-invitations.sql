-- liquibase formatted sql

-- changeset goaldone:0008-add-role-to-invitations
-- comment: Add role to invitations to allow inviting new ADMINs.
ALTER TABLE invitations ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- rollback ALTER TABLE invitations DROP COLUMN role;
