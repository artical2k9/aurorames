-- V004: Seed routing privileges and assign to SYSTEM_ADMIN (all) and ENGINEER (view + authoring).
--
-- PREREQUISITE: routing_user must have INSERT on iam.privilege and iam.role_privilege.
-- In Docker Compose this is handled by docker/postgres/20-cross-schema-grants.sh (init script).
-- In production PostgreSQL, run as a DBA before deploying this service:
--   GRANT USAGE ON SCHEMA iam TO routing_user;
--   GRANT SELECT, INSERT ON iam.privilege, iam.role_privilege, iam.role TO routing_user;

INSERT INTO iam.privilege (privilege_key, module_name, description, registered_by_service)
VALUES
    ('routing:route:view',       'routing', 'View routes, operations, groups and steps',           'routing-service'),
    ('routing:route:manage',     'routing', 'Create and edit routes, operations, groups, steps',   'routing-service'),
    ('routing:route:approve',    'routing', 'Submit/approve/reject route revisions (e-signature)',  'routing-service'),
    ('routing:operation:approve','routing', 'Approve operation-level revisions',                    'routing-service'),
    ('routing:settings:view',    'routing', 'View routing reference data (Settings submodule)',      'routing-service'),
    ('routing:settings:manage',  'routing', 'Manage routing reference data (Settings submodule)',    'routing-service')
ON CONFLICT (privilege_key) DO NOTHING;

-- SYSTEM_ADMIN: all routing privileges.
INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'SYSTEM_ADMIN'
  AND  r.is_system_role = true
  AND  p.privilege_key LIKE 'routing:%'
ON CONFLICT (role_id, privilege_id) DO NOTHING;

-- ENGINEER: author routes + view reference data (routing is a manufacturing-engineering activity).
INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'ENGINEER'
  AND  r.is_system_role = true
  AND  p.privilege_key IN ('routing:route:view', 'routing:route:manage',
                           'routing:settings:view')
ON CONFLICT (role_id, privilege_id) DO NOTHING;
