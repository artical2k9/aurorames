-- V001: Create iam schema and all base tables.

CREATE SCHEMA IF NOT EXISTS iam;

CREATE TABLE iam.organisation (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    keycloak_group_id   VARCHAR(100) NOT NULL,
    keycloak_group_name VARCHAR(100) NOT NULL,
    is_active           BOOLEAN      NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(200) NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(200) NOT NULL,
    CONSTRAINT pk_organisation      PRIMARY KEY (id),
    CONSTRAINT uq_organisation_kc   UNIQUE (keycloak_group_id)
);

CREATE TABLE iam.role (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    name             VARCHAR(100) NOT NULL,
    description      VARCHAR(500),
    org_id           UUID         NOT NULL,
    is_system_role   BOOLEAN      NOT NULL DEFAULT false,
    keycloak_role_id VARCHAR(100),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       VARCHAR(200) NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by       VARCHAR(200) NOT NULL,
    CONSTRAINT pk_role         PRIMARY KEY (id),
    CONSTRAINT uq_role_name    UNIQUE (name, org_id),
    CONSTRAINT fk_role_org     FOREIGN KEY (org_id) REFERENCES iam.organisation (id)
);

CREATE INDEX idx_role_org_id ON iam.role (org_id);

CREATE TABLE iam.privilege (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    privilege_key         VARCHAR(200) NOT NULL,
    module_name           VARCHAR(100) NOT NULL,
    description           VARCHAR(500),
    registered_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    registered_by_service VARCHAR(100) NOT NULL,
    CONSTRAINT pk_privilege     PRIMARY KEY (id),
    CONSTRAINT uq_privilege_key UNIQUE (privilege_key)
);

CREATE INDEX idx_privilege_module ON iam.privilege (module_name);

CREATE TABLE iam.role_privilege (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    role_id      UUID         NOT NULL,
    privilege_id UUID         NOT NULL,
    org_id       UUID         NOT NULL,
    granted_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    granted_by   VARCHAR(200) NOT NULL,
    revoked_at   TIMESTAMPTZ,
    revoked_by   VARCHAR(200),
    CONSTRAINT pk_role_privilege   PRIMARY KEY (id),
    CONSTRAINT uq_role_privilege   UNIQUE (role_id, privilege_id),
    CONSTRAINT fk_rp_role          FOREIGN KEY (role_id)      REFERENCES iam.role (id),
    CONSTRAINT fk_rp_privilege     FOREIGN KEY (privilege_id) REFERENCES iam.privilege (id),
    CONSTRAINT fk_rp_org           FOREIGN KEY (org_id)       REFERENCES iam.organisation (id)
);

CREATE INDEX idx_role_privilege_org_id  ON iam.role_privilege (org_id);
CREATE INDEX idx_role_privilege_role_id ON iam.role_privilege (role_id);
