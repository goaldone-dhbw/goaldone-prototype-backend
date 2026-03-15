-- liquibase formatted sql

-- changeset goaldone:0007-seed-dev-user
-- comment: Seed a test organization and user for development.
--          Only active if profile 'dev' is used (handled by spring.liquibase.contexts).

INSERT INTO organizations (id, name, admin_email, allowed_domain)
VALUES ('00000000-0000-0000-0000-000000000001', 'Test Organization', 'admin@test.de', 'test.de');

INSERT INTO users (id, email, first_name, last_name, password_hash, role, organization_id)
VALUES ('00000000-0000-0000-0000-000000000002', 'test@test.de', 'Test', 'User', '$2a$10$YCsOjUbspCubi3FolPCuOOEBJV0/ced.uwHXtnZudFYDQkzCJ.W9.', 'USER', '00000000-0000-0000-0000-000000000001');

-- rollback DELETE FROM users WHERE id = '00000000-0000-0000-0000-000000000002';
-- rollback DELETE FROM organizations WHERE id = '00000000-0000-0000-0000-000000000001';
