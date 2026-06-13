-- V004: Seed labour privileges and assign to SYSTEM_ADMIN (all) and ENGINEER (view + qualification).
--
-- PREREQUISITE: labour_user must have INSERT on iam.privilege and iam.role_privilege.
-- In Docker Compose this is handled by docker/postgres/20-cross-schema-grants.sh (init script).
-- In production PostgreSQL, run as a DBA before deploying this service:
--   GRANT USAGE ON SCHEMA iam TO labour_user;
--   GRANT SELECT, INSERT ON iam.privilege, iam.role_privilege, iam.role TO labour_user;

INSERT INTO iam.privilege (privilege_key, module_name, description, registered_by_service)
VALUES
    ('labour:employee:view',       'labour', 'View employees and competency profiles',               'labour-service'),
    ('labour:employee:manage',     'labour', 'Create, edit, activate/deactivate employees',          'labour-service'),
    ('labour:skill:view',          'labour', 'View the skill catalogue',                             'labour-service'),
    ('labour:skill:manage',        'labour', 'Create, edit, deactivate skills',                      'labour-service'),
    ('labour:certification:view',  'labour', 'View certifications and expiry status',                'labour-service'),
    ('labour:certification:manage','labour', 'Award and revoke skill certifications',                'labour-service'),
    ('labour:training:view',       'labour', 'View training events and attendance history',          'labour-service'),
    ('labour:training:manage',     'labour', 'Record and edit training events',                      'labour-service'),
    ('labour:qualification:view',  'labour', 'Evaluate operator qualification against skill lists',  'labour-service')
ON CONFLICT (privilege_key) DO NOTHING;

-- SYSTEM_ADMIN: all labour privileges.
INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'SYSTEM_ADMIN'
  AND  r.is_system_role = true
  AND  p.privilege_key LIKE 'labour:%'
ON CONFLICT (role_id, privilege_id) DO NOTHING;

-- ENGINEER: read-side labour data + qualification evaluation (consumed by work-instruction gating).
INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'ENGINEER'
  AND  r.is_system_role = true
  AND  p.privilege_key IN ('labour:employee:view', 'labour:skill:view',
                           'labour:certification:view', 'labour:training:view',
                           'labour:qualification:view')
ON CONFLICT (role_id, privilege_id) DO NOTHING;
