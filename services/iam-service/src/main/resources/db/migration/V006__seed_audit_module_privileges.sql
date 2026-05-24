-- V006: Seed audit module privileges and assign them to system roles.
-- AUDIT_READ is granted to ADMIN; SYSTEM_ADMIN already covers all privileges via wildcard.

INSERT INTO iam.privilege (privilege_key, module_name, description, registered_by_service)
VALUES
    ('audit:events:read',  'audit', 'Read audit trail records and auth events',          'audit-service'),
    ('audit:verify:run',   'audit', 'Run tamper-evidence verification (SYSTEM_ADMIN)',   'audit-service')
ON CONFLICT (privilege_key) DO NOTHING;

INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'ADMIN'
  AND  r.is_system_role = true
  AND  p.module_name = 'audit'
ON CONFLICT (role_id, privilege_id) DO NOTHING;
