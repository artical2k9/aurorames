-- V007: Seed item-master module privileges and assign to SYSTEM_ADMIN and ENGINEER roles.

INSERT INTO iam.privilege (privilege_key, module_name, description, registered_by_service)
VALUES
    ('item-master:records:view',   'item-master', 'View item master records',                         'work-order-service'),
    ('item-master:records:manage', 'item-master', 'Create, update, and obsolete item master records', 'work-order-service'),
    ('item-master:bom:manage',     'item-master', 'Create, update, and release bills of materials',   'work-order-service'),
    ('item-master:eco:manage',     'item-master', 'Create, approve, and implement engineering change orders', 'work-order-service'),
    ('item-master:udf:manage',     'item-master', 'Define and manage user-defined fields for item master', 'work-order-service')
ON CONFLICT (privilege_key) DO NOTHING;

-- Grant all 5 privileges to SYSTEM_ADMIN
INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'SYSTEM_ADMIN'
  AND  r.is_system_role = true
  AND  p.module_name = 'item-master'
ON CONFLICT (role_id, privilege_id) DO NOTHING;

-- Grant view + manage records + bom:manage + eco:manage to ENGINEER
INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'ENGINEER'
  AND  r.is_system_role = true
  AND  p.privilege_key IN (
      'item-master:records:view',
      'item-master:records:manage',
      'item-master:bom:manage',
      'item-master:eco:manage'
  )
ON CONFLICT (role_id, privilege_id) DO NOTHING;
