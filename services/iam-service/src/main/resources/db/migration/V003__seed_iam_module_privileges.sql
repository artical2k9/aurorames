-- V003: Seed IAM module privileges and assign them all to the ADMIN system role.

INSERT INTO iam.privilege (privilege_key, module_name, description, registered_by_service)
VALUES
    ('iam:users:create', 'iam', 'Create and invite users',                              'iam-service'),
    ('iam:users:view',   'iam', 'View users and their role assignments',                'iam-service'),
    ('iam:roles:manage', 'iam', 'Create, modify, and delete roles; assign privileges',  'iam-service'),
    ('iam:esig:sign',    'iam', 'Apply electronic signature on regulated records',      'iam-service');

INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'ADMIN'
  AND  r.is_system_role = true
  AND  p.module_name = 'iam';
