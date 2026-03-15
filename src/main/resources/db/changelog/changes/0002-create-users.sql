-- liquibase formatted sql

-- changeset goaldone:0002-create-users
-- comment: Platform users. organization_id is nullable — SUPER_ADMINs without
--          an org assignment have NULL here. All other roles always belong to an org.
--          password_hash stores a BCrypt hash, never a plain-text password.

CREATE TABLE users (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    organization_id UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_users               PRIMARY KEY (id),
    CONSTRAINT uq_users_email         UNIQUE (email),
    CONSTRAINT fk_users_organization  FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE SET NULL,
    CONSTRAINT chk_users_role CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'USER'))
);

CREATE INDEX idx_users_email           ON users (email);
CREATE INDEX idx_users_organization_id ON users (organization_id);
CREATE INDEX idx_users_role            ON users (role);

-- rollback DROP TABLE users;

-- changeset goaldone:0002-create-refresh-tokens
-- comment: Stores hashed refresh tokens for JWT token rotation.
--          The raw token is NEVER persisted — only a SHA-256 hash.
--          revoked_at is set immediately when a token is rotated or the user logs out.
--          Expired and revoked tokens should be purged periodically.

CREATE TABLE refresh_tokens (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    revoked_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_refresh_tokens              PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token_hash   UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user         FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

-- Partial index: fast lookup of only active (non-revoked, non-expired) tokens
CREATE INDEX idx_refresh_tokens_active ON refresh_tokens (token_hash, expires_at);

-- rollback DROP TABLE refresh_tokens;
