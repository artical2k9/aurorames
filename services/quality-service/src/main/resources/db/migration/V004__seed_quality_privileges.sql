-- V004: Seed quality inspection-plan privileges and assign to SYSTEM_ADMIN (all) and ENGINEER (read).
--
-- PREREQUISITE: quality_user must have INSERT on iam.privilege and iam.role_privilege.
-- In Docker Compose this is handled by docker/postgres/20-cross-schema-grants.sh (init script).
-- In production PostgreSQL, run as a DBA before deploying this service:
--   GRANT USAGE ON SCHEMA iam TO quality_user;
--   GRANT SELECT, INSERT ON iam.privilege, iam.role_privilege, iam.role TO quality_user;

INSERT INTO iam.privilege (privilege_key, module_name, description, registered_by_service)
VALUES
    ('quality:inspection-plan:create', 'quality', 'Create inspection plans (control plans)', 'quality-service'),
    ('quality:inspection-plan:read',   'quality', 'View inspection plans and characteristics', 'quality-service'),
    ('quality:inspection-plan:update', 'quality', 'Edit inspection plan drafts and characteristics', 'quality-service'),
    ('quality:inspection-plan:delete', 'quality', 'Delete never-approved inspection plans', 'quality-service'),
    ('quality:inspection-plan:approve', 'quality', 'Submit, approve, and reject inspection plan revisions', 'quality-service')
ON CONFLICT (privilege_key) DO NOTHING;

-- SYSTEM_ADMIN: all quality privileges.
INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'SYSTEM_ADMIN'
  AND  r.is_system_role = true
  AND  p.privilege_key LIKE 'quality:inspection-plan:%'
ON CONFLICT (role_id, privilege_id) DO NOTHING;

-- ENGINEER: read only.
INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'ENGINEER'
  AND  r.is_system_role = true
  AND  p.privilege_key = 'quality:inspection-plan:read'
ON CONFLICT (role_id, privilege_id) DO NOTHING;
