-- V006: Seed ECO privilege for engineering-service and assign to SYSTEM_ADMIN and ENGINEER.
--
-- PREREQUISITE: engineering_user must have INSERT on iam.privilege and iam.role_privilege.
-- In Docker Compose this is handled by docker/postgres/20-cross-schema-grants.sh (init script).
-- In production PostgreSQL, run as a DBA before deploying this service:
--   GRANT USAGE ON SCHEMA iam TO engineering_user;
--   GRANT SELECT, INSERT ON iam.privilege, iam.role_privilege, iam.role TO engineering_user;

INSERT INTO iam.privilege (privilege_key, module_name, description, registered_by_service)
VALUES
    ('item-master:eco:manage', 'item-master', 'Create, approve, and implement engineering change orders', 'engineering-service')
ON CONFLICT (privilege_key) DO NOTHING;

INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'SYSTEM_ADMIN'
  AND  r.is_system_role = true
  AND  p.privilege_key = 'item-master:eco:manage'
ON CONFLICT (role_id, privilege_id) DO NOTHING;

INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'ENGINEER'
  AND  r.is_system_role = true
  AND  p.privilege_key = 'item-master:eco:manage'
ON CONFLICT (role_id, privilege_id) DO NOTHING;
