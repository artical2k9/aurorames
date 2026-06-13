-- V008: Seed Work Instructions privileges and assign to SYSTEM_ADMIN (all) and ENGINEER (author+approve).
--
-- PREREQUISITE: engineering_user must have INSERT on iam.privilege and iam.role_privilege
-- (handled by docker/postgres/20-cross-schema-grants.sh; see V006 header for the prod grants).

INSERT INTO iam.privilege (privilege_key, module_name, description, registered_by_service)
VALUES
    ('engineering:work-instruction:create', 'engineering', 'Create work instructions', 'engineering-service'),
    ('engineering:work-instruction:read',   'engineering', 'View work instructions and revisions', 'engineering-service'),
    ('engineering:work-instruction:update', 'engineering', 'Edit work instruction drafts, steps, media, skills', 'engineering-service'),
    ('engineering:work-instruction:delete', 'engineering', 'Delete never-approved work instructions', 'engineering-service'),
    ('engineering:work-instruction:approve', 'engineering', 'Submit, approve (e-sign), and reject work instruction revisions', 'engineering-service')
ON CONFLICT (privilege_key) DO NOTHING;

-- SYSTEM_ADMIN: all work-instruction privileges.
INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'SYSTEM_ADMIN'
  AND  r.is_system_role = true
  AND  p.privilege_key LIKE 'engineering:work-instruction:%'
ON CONFLICT (role_id, privilege_id) DO NOTHING;

-- ENGINEER: author + approve (create/read/update/approve), not delete.
INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'ENGINEER'
  AND  r.is_system_role = true
  AND  p.privilege_key IN (
        'engineering:work-instruction:create',
        'engineering:work-instruction:read',
        'engineering:work-instruction:update',
        'engineering:work-instruction:approve')
ON CONFLICT (role_id, privilege_id) DO NOTHING;
