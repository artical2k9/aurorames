-- V002: Create application DB roles and enforce immutability
-- MES-7: System Activity & Audit Logging
--
-- audit_service: application role — INSERT + SELECT only (no UPDATE or DELETE)
-- audit_flyway:  migration role  — full DDL rights on the audit schema

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'audit_service') THEN
        CREATE ROLE audit_service WITH LOGIN PASSWORD 'changeme-override-in-env';
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'audit_flyway') THEN
        CREATE ROLE audit_flyway WITH LOGIN PASSWORD 'changeme-override-in-env';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA audit TO audit_service;
GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA audit TO audit_service;
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT SELECT, INSERT ON TABLES TO audit_service;

GRANT USAGE ON SCHEMA audit TO audit_flyway;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA audit TO audit_flyway;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA audit TO audit_flyway;
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT ALL ON TABLES TO audit_flyway;
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT ALL ON SEQUENCES TO audit_flyway;

-- Enforce immutability: application role must never UPDATE or DELETE audit rows
REVOKE UPDATE, DELETE ON audit.audit_records       FROM audit_service;
REVOKE UPDATE, DELETE ON audit.auth_audit_records  FROM audit_service;
