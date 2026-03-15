-- liquibase formatted sql

-- changeset goaldone:0003-create-invitations
-- comment: Invitation links sent by an ADMIN to onboard new users.
--          token is a UUID string embedded in the invite URL — treated as a secret,
--          so it is indexed for O(1) lookup on acceptance.
--          expires_at is set to now() + 48h on creation.
--          Once accepted, the row is deleted (the user account is the proof of acceptance).
--          The unique constraint on (email, organization_id) prevents spamming
--          a user with multiple open invitations for the same org.

CREATE TABLE invitations (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    organization_id UUID         NOT NULL,
    invited_by      UUID,
    token           VARCHAR(255) NOT NULL,
    expires_at      TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_invitations                  PRIMARY KEY (id),
    CONSTRAINT uq_invitations_token            UNIQUE (token),
    CONSTRAINT uq_invitations_email_org        UNIQUE (email, organization_id),
    CONSTRAINT fk_invitations_organization     FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_invitations_invited_by       FOREIGN KEY (invited_by)
        REFERENCES users (id) ON DELETE SET NULL
);

-- Index: token lookup on invitation acceptance (hot path)
CREATE INDEX idx_invitations_token           ON invitations (token);
CREATE INDEX idx_invitations_organization_id ON invitations (organization_id);

-- Index: list open invitations per org
CREATE INDEX idx_invitations_open ON invitations (organization_id, expires_at);

-- rollback DROP TABLE invitations;
