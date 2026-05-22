-- V004: Hibernate Envers audit tables for organisation, role, and role_privilege.

CREATE TABLE iam.revinfo (
    rev      INT4 NOT NULL,
    revtstmp INT8,
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

CREATE SEQUENCE iam.revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE iam.organisation_aud (
    id                  UUID         NOT NULL,
    rev                 INT4         NOT NULL,
    revtype             INT2,
    name                VARCHAR(200),
    keycloak_group_id   VARCHAR(100),
    keycloak_group_name VARCHAR(100),
    is_active           BOOLEAN,
    created_at          TIMESTAMPTZ,
    created_by          VARCHAR(200),
    updated_at          TIMESTAMPTZ,
    updated_by          VARCHAR(200),
    CONSTRAINT pk_organisation_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_organisation_aud_rev FOREIGN KEY (rev) REFERENCES iam.revinfo (rev)
);

CREATE TABLE iam.role_aud (
    id               UUID         NOT NULL,
    rev              INT4         NOT NULL,
    revtype          INT2,
    name             VARCHAR(100),
    description      VARCHAR(500),
    org_id           UUID,
    is_system_role   BOOLEAN,
    keycloak_role_id VARCHAR(100),
    created_at       TIMESTAMPTZ,
    created_by       VARCHAR(200),
    updated_at       TIMESTAMPTZ,
    updated_by       VARCHAR(200),
    CONSTRAINT pk_role_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_role_aud_rev FOREIGN KEY (rev) REFERENCES iam.revinfo (rev)
);

CREATE TABLE iam.role_privilege_aud (
    id           UUID         NOT NULL,
    rev          INT4         NOT NULL,
    revtype      INT2,
    role_id      UUID,
    privilege_id UUID,
    org_id       UUID,
    granted_at   TIMESTAMPTZ,
    granted_by   VARCHAR(200),
    revoked_at   TIMESTAMPTZ,
    revoked_by   VARCHAR(200),
    CONSTRAINT pk_role_privilege_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_role_privilege_aud_rev FOREIGN KEY (rev) REFERENCES iam.revinfo (rev)
);
