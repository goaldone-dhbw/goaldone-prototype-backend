-- liquibase formatted sql

-- changeset goaldone:0008-seed-super-admin
-- comment: Seed a super admin for development testing.
--          Only active if profile 'dev' is used.

INSERT INTO users (id, email, first_name, last_name, password_hash, role)
VALUES ('00000000-0000-0000-0000-000000000004', 'superadmin@goaldone.de', 'Super', 'Admin', '$2a$10$YCsOjUbspCubi3FolPCuOOEBJV0/ced.uwHXtnZudFYDQkzCJ.W9.', 'SUPER_ADMIN');

-- rollback DELETE FROM users WHERE id = '00000000-0000-0000-0000-000000000004';
