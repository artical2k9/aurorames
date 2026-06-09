-- V013: Seed AS5553 counterfeit risk privilege and QUALITY_ENGINEER role.
--
-- Adds item-master:as5553:manage privilege, creates the QUALITY_ENGINEER system role,
-- and grants it records:view + as5553:manage.  SYSTEM_ADMIN also receives the new privilege.

INSERT INTO iam.privilege (privilege_key, module_name, description, registered_by_service)
VALUES ('item-master:as5553:manage', 'item-master',
        'Manage AS5553 counterfeit risk fields on item master records', 'inventory-service')
ON CONFLICT (privilege_key) DO NOTHING;

-- Create QUALITY_ENGINEER role if it does not already exist
INSERT INTO iam.role (name, description, org_id, is_system_role)
SELECT 'QUALITY_ENGINEER',
       'Quality engineer — manages AS5553 counterfeit risk fields',
       '00000000-0000-0000-0000-000000000001',
       true
WHERE NOT EXISTS (
    SELECT 1 FROM iam.role
    WHERE name = 'QUALITY_ENGINEER'
      AND org_id = '00000000-0000-0000-0000-000000000001'
);

-- Grant records:view + as5553:manage to QUALITY_ENGINEER
INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'QUALITY_ENGINEER'
  AND  r.is_system_role = true
  AND  p.privilege_key IN ('item-master:records:view', 'item-master:as5553:manage')
ON CONFLICT (role_id, privilege_id) DO NOTHING;

-- Grant as5553:manage to SYSTEM_ADMIN (new privilege not in scope of V007's module-wide grant)
INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'SYSTEM_ADMIN'
  AND  r.is_system_role = true
  AND  p.privilege_key = 'item-master:as5553:manage'
ON CONFLICT (role_id, privilege_id) DO NOTHING;
