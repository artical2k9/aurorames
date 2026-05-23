-- V005: Seed platform module privileges and assign them to the ADMIN system role.

INSERT INTO iam.privilege (privilege_key, module_name, description, registered_by_service)
VALUES
    ('platform:config:manage', 'platform', 'Create, update, and delete platform configuration entries', 'platform-service'),
    ('platform:config:read',   'platform', 'Read platform configuration entries',                       'platform-service')
ON CONFLICT (privilege_key) DO NOTHING;

INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'ADMIN'
  AND  r.is_system_role = true
  AND  p.module_name = 'platform'
ON CONFLICT (role_id, privilege_id) DO NOTHING;
