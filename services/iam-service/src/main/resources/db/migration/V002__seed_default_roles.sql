-- V002: Seed SYSTEM organisation and the six global default roles.

INSERT INTO iam.organisation (id, name, keycloak_group_id, keycloak_group_name, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-000000000001', 'SYSTEM', 'system', 'system', 'migration', 'migration');

INSERT INTO iam.role (org_id, name, is_system_role, created_by, updated_by)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'SYSTEM_ADMIN',      true, 'migration', 'migration'),
    ('00000000-0000-0000-0000-000000000001', 'OPERATOR',          true, 'migration', 'migration'),
    ('00000000-0000-0000-0000-000000000001', 'QUALITY_INSPECTOR', true, 'migration', 'migration'),
    ('00000000-0000-0000-0000-000000000001', 'PLANNER',           true, 'migration', 'migration'),
    ('00000000-0000-0000-0000-000000000001', 'ENGINEER',          true, 'migration', 'migration'),
    ('00000000-0000-0000-0000-000000000001', 'VIEWER',            true, 'migration', 'migration');
