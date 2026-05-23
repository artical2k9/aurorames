-- V001: Create platform schema and system_configuration table.

CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE platform.system_configuration (
    id             BIGSERIAL        PRIMARY KEY,
    org_id         UUID             NOT NULL,
    config_key     VARCHAR(255)     NOT NULL,
    config_value   TEXT,
    description    VARCHAR(1000),
    active         BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(255),
    CONSTRAINT uq_syscfg_org_key UNIQUE (org_id, config_key)
);

CREATE INDEX idx_syscfg_org_id  ON platform.system_configuration (org_id);
CREATE INDEX idx_syscfg_org_key ON platform.system_configuration (org_id, config_key);
