-- liquibase formatted sql

-- changeset goaldone:0001-create-organizations
-- comment: Core tenant table. Every other resource belongs to an organization.
--          allowed_domain is optional — if set, users with that email domain
--          can self-register without an invite link.

CREATE TABLE organizations (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL,
    admin_email    VARCHAR(255) NOT NULL,
    allowed_domain VARCHAR(255),
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_organizations PRIMARY KEY (id),
    CONSTRAINT uq_organizations_name UNIQUE (name)
);

-- Index: admin lookups by email (e.g. checking if admin exists on org creation)
CREATE INDEX idx_organizations_admin_email  ON organizations (admin_email);

-- Index: domain-whitelist check on self-registration
CREATE INDEX idx_organizations_allowed_domain ON organizations (allowed_domain);

-- rollback DROP TABLE organizations;
