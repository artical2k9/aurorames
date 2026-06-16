-- MES-9 FR-031/032/033: route edit-lock. A route is edited under an exclusive lock held by one
-- user; others are read-only until the holder unlocks or the route is approved. A privileged user
-- (routing:route:unlock) can force-unlock. Lock columns are Envers-audited (route is @Audited).

ALTER TABLE routing.route     ADD COLUMN lock_holder VARCHAR(255);
ALTER TABLE routing.route     ADD COLUMN locked_at   TIMESTAMPTZ;
ALTER TABLE routing.route_aud ADD COLUMN lock_holder VARCHAR(255);
ALTER TABLE routing.route_aud ADD COLUMN locked_at   TIMESTAMPTZ;

-- Force-unlock privilege (FR-033). Granted to SYSTEM_ADMIN; other roles assigned via the Role UI.
INSERT INTO iam.privilege (privilege_key, module_name, description, registered_by_service)
VALUES ('routing:route:unlock', 'routing', 'Force-unlock a route locked by another user', 'routing-service')
ON CONFLICT (privilege_key) DO NOTHING;

INSERT INTO iam.role_privilege (role_id, privilege_id, org_id, granted_by)
SELECT r.id, p.id, r.org_id, 'migration'
FROM   iam.role r
CROSS JOIN iam.privilege p
WHERE  r.name = 'SYSTEM_ADMIN'
  AND  r.is_system_role = true
  AND  p.privilege_key = 'routing:route:unlock'
ON CONFLICT (role_id, privilege_id) DO NOTHING;
