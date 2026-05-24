-- V001: Create audit schema and tables
-- MES-7: System Activity & Audit Logging

CREATE SCHEMA IF NOT EXISTS audit;

-- Shared revision entity for Hibernate Envers (ValidityAuditStrategy)
-- Column names match DefaultRevisionEntity field names (id, timestamp).
CREATE TABLE audit.mes_revisions (
    id              INTEGER      NOT NULL,
    timestamp       BIGINT       NOT NULL,
    user_id         VARCHAR(100) NOT NULL,
    service_source  VARCHAR(100) NOT NULL,
    CONSTRAINT pk_mes_revisions PRIMARY KEY (id)
);
CREATE SEQUENCE audit.mes_revisions_seq START WITH 1 INCREMENT BY 50;

-- Core immutable audit record store for Kafka-published business events
CREATE TABLE audit.audit_records (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_id        UUID         NOT NULL,
    event_type      VARCHAR(30)  NOT NULL,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       VARCHAR(100) NOT NULL,
    user_id         VARCHAR(100) NOT NULL,
    service_source  VARCHAR(100) NOT NULL,
    action          VARCHAR(20)  NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    previous_state  JSONB,
    new_state       JSONB,
    checksum        VARCHAR(64)  NOT NULL,
    schema_version  INTEGER      NOT NULL DEFAULT 1,
    tenant_id       UUID,
    CONSTRAINT pk_audit_records          PRIMARY KEY (id),
    CONSTRAINT uq_audit_records_event_id UNIQUE      (event_id)
);

CREATE INDEX idx_audit_records_entity ON audit.audit_records (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_records_user   ON audit.audit_records (user_id, occurred_at DESC);
CREATE INDEX idx_audit_records_time   ON audit.audit_records (occurred_at DESC);
CREATE INDEX idx_audit_records_tenant ON audit.audit_records (tenant_id, occurred_at DESC);

-- Keycloak authentication and authorisation events
CREATE TABLE audit.auth_audit_records (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_id        UUID         NOT NULL,
    event_type      VARCHAR(60)  NOT NULL,
    user_id         VARCHAR(100),
    client_id       VARCHAR(100),
    ip_address      VARCHAR(45),
    session_id      VARCHAR(100),
    realm_id        VARCHAR(100) NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    details         JSONB,
    tenant_id       UUID,
    CONSTRAINT pk_auth_audit_records          PRIMARY KEY (id),
    CONSTRAINT uq_auth_audit_records_event_id UNIQUE      (event_id)
);

CREATE INDEX idx_auth_audit_user ON audit.auth_audit_records (user_id, occurred_at DESC);
CREATE INDEX idx_auth_audit_type ON audit.auth_audit_records (event_type, occurred_at DESC);
CREATE INDEX idx_auth_audit_time ON audit.auth_audit_records (occurred_at DESC);
